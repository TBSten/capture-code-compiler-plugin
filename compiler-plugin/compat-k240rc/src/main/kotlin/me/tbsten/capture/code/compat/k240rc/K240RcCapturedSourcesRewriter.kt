// task-076 / Kotlin 2.4.0-RC drift:
//   - IrMemberAccessExpression.putValueArgument(i, expr) -> arguments[i] = expr (via index, ArrayList<IrExpression?>)
//   - IrMemberAccessExpression.putTypeArgument(i, type)  -> (typeArguments as MutableList<IrType?>)[i] = type
//   - IrFunction.valueParameters -> IrFunction.nonDispatchParameters (IrUtilsKt extension)
//   - IrValueParameter.varargElementType still exists on the parameter itself
//   - IrConstructorCall.getValueArgument(i) -> arguments[i]
// 旧 API は 2.4.0-RC で完全削除 (deprecated ERROR ではなくシンボル無し)。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.k240rc.filler.CaptureKindFillerBuilder
import me.tbsten.capture.code.compat.k240rc.filler.FillerBuilder
import me.tbsten.capture.code.compat.k240rc.filler.SourceFillerBuilder
import me.tbsten.capture.code.compat.k240rc.filler.SourceLocationFillerBuilder
import me.tbsten.capture.code.compat.k240rc.userargs.UserArgIrBuilder
import me.tbsten.capture.code.compat.k240rc.userargs.UserArgPrimitiveIrBuilder
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Logic H (`capturedSources<T>()` 書き換え) の Kotlin 2.4.0-RC 向け実装。
 *
 * 入力された [IrCall] (= `me.tbsten.capture.code.capturedSources<Marker>()`、`Marker` は
 * [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry] に登録された marker のいずれか) を、
 * [K240RcCapturedSourcesCollector] が収集した [K240RcCapturedSiteData] のリストから組み立てた
 * `listOf(Marker(...))` 相当の [IrCall] (`kotlin.collections.listOf`) に書き換える。
 *
 * ## 構築方針
 *
 * - **filler 3 種すべての自動値埋め**: `source: Source` / `location: SourceLocation` /
 *   `kind: CaptureKind` の filler 3 種を統一的に扱うため、 `filler/` パッケージ配下の builder
 *   クラス群に切り出している。 `buildSnippetsCall` は marker constructor の各 parameter を順に
 *   走査し、 その type が filler 型に該当すれば対応する [FillerBuilder] に dispatch する。
 * - **filler 識別は型ベース**: ユーザ定義パラメータとの境界はここで明確にしている。
 *   parameter の `type` が `me.tbsten.capture.code.{Source, SourceLocation, CaptureKind}` の
 *   いずれかと **等しい** 場合のみ filler、 それ以外はユーザ定義扱い。 design §3.2 / §3.3。
 *
 * ## 2.4.0-RC drift (task-076)
 *
 * - `IrFunction.valueParameters` は 2.4.0-RC で削除され、 [nonDispatchParameters] に統合された。
 *   Context-receiver / extension-receiver / regular parameter を区別したい場合は
 *   [org.jetbrains.kotlin.ir.declarations.IrParameterKind] で filter する。
 * - `putValueArgument` / `putTypeArgument` / `getValueArgument(Int)` / `getTypeArgument(Int)` も
 *   削除され、 `arguments` / `typeArguments` を MutableList として扱う。
 */
internal object K240RcCapturedSourcesRewriter {

    fun rewriteCapturedSourcesCall(
        original: IrCall,
        markerFqn: String,
        siteData: List<K240RcCapturedSiteData>,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ): IrCall? {
        val markerClassId = ClassId.topLevel(FqName(markerFqn))
        val markerSymbol = pluginContext.referenceClass(markerClassId) ?: return null
        val markerConstructor = markerSymbol.primaryConstructorOrNull() ?: return null

        val fillerPlan = buildFillerPlan(markerConstructor, pluginContext) ?: return null

        val listOfSymbol = pluginContext.findListOfVararg() ?: return null
        val markerType = markerSymbol.defaultTypeProjection()
        val listElements = siteData.map { data ->
            buildMarkerInstance(
                data = data,
                markerType = markerType,
                markerConstructor = markerConstructor,
                fillerPlan = fillerPlan,
                config = data.effectiveConfig,
                pluginContext = pluginContext,
            )
        }

        val listType = pluginContext.irBuiltIns.listClass.typeWith(markerType)
        val varargType = pluginContext.irBuiltIns.arrayClass.typeWith(markerType)

        return IrCallImpl(
            startOffset = original.startOffset,
            endOffset = original.endOffset,
            type = listType,
            symbol = listOfSymbol,
            typeArgumentsCount = 1,
        ).apply {
            setTypeArgumentSafe(0, markerType)
            putArgumentSafe(
                0,
                IrVarargImpl(
                    startOffset = original.startOffset,
                    endOffset = original.endOffset,
                    type = varargType,
                    varargElementType = markerType,
                    elements = listElements.toList<IrVarargElement>(),
                ),
            )
        }
    }

    private class FillerPlan(
        val bindings: Map<Int, FillerBuilder>,
    )

    private fun buildFillerPlan(
        markerConstructor: IrConstructorSymbol,
        pluginContext: IrPluginContext,
    ): FillerPlan? {
        val sourceFqn = CaptureCodeFillerClassIds.Source.asFqNameString()
        val locationFqn = CaptureCodeFillerClassIds.SourceLocation.asFqNameString()
        val captureKindFqn = CaptureCodeFillerClassIds.CaptureKind.asFqNameString()

        val parameters = markerConstructor.owner.nonDispatchParameters
        val bindings = mutableMapOf<Int, FillerBuilder>()

        var sourceBuilder: SourceFillerBuilder? = null
        var locationBuilder: SourceLocationFillerBuilder? = null
        var kindBuilder: CaptureKindFillerBuilder? = null

        parameters.forEachIndexed { index, param ->
            val paramTypeFqn = param.type.classFqName?.asString()
            when (paramTypeFqn) {
                sourceFqn -> {
                    val builder = sourceBuilder ?: SourceFillerBuilder.resolve(pluginContext)
                        ?: return null
                    sourceBuilder = builder
                    bindings[index] = builder
                }
                locationFqn -> {
                    val builder = locationBuilder ?: SourceLocationFillerBuilder.resolve(pluginContext)
                        ?: return null
                    locationBuilder = builder
                    bindings[index] = builder
                }
                captureKindFqn -> {
                    val builder = kindBuilder ?: CaptureKindFillerBuilder.resolve(pluginContext)
                        ?: return null
                    kindBuilder = builder
                    bindings[index] = builder
                }
                else -> {
                    // ユーザ定義パラメータ。
                }
            }
        }

        return FillerPlan(bindings)
    }

    private fun buildMarkerInstance(
        data: K240RcCapturedSiteData,
        markerType: IrType,
        markerConstructor: IrConstructorSymbol,
        fillerPlan: FillerPlan,
        config: CaptureCodePluginConfig,
        pluginContext: IrPluginContext,
    ): IrExpression {
        val markerCall = data.markerCall
        val site = data.site
        val parameters = markerConstructor.owner.nonDispatchParameters

        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = markerType,
            constructorSymbol = markerConstructor,
        ).apply {
            parameters.forEachIndexed { index, parameter ->
                val fillerBuilder = fillerPlan.bindings[index]
                if (fillerBuilder != null) {
                    putArgumentSafe(index, fillerBuilder.build(site, config))
                } else {
                    val userExpr = if (markerCall != null) {
                        UserArgIrBuilder.buildOrDefault(markerCall, index, parameter)
                    } else {
                        val name = parameter.name.asString()
                        val pushed = data.expressionUserArgs[name]
                        UserArgPrimitiveIrBuilder.buildOrNull(pushed, parameter, pluginContext)
                            ?: UserArgIrBuilder.buildOrDefault(null, index, parameter)
                    }
                    if (userExpr != null) {
                        putArgumentSafe(index, userExpr)
                    }
                }
            }
        }
    }

    private fun IrClassSymbol.primaryConstructorOrNull(): IrConstructorSymbol? =
        owner.constructors.firstOrNull { it.isPrimary }?.symbol
            ?: owner.constructors.firstOrNull()?.symbol

    private fun IrClassSymbol.defaultTypeProjection(): IrType = typeWith()

    /**
     * `kotlin.collections.listOf(vararg elements: T): List<T>` の symbol を解決する。
     *
     * 2.4.0-RC で `valueParameters` が削除されたため、 [nonDispatchParameters] (= regular +
     * context + extension receiver) を使う。 top-level `listOf` の場合 receiver はないので
     * `nonDispatchParameters.size == 1` (vararg) で一意に絞り込める。
     */
    private fun IrPluginContext.findListOfVararg(): IrSimpleFunctionSymbol? {
        val callableId = CallableId(
            packageName = FqName("kotlin.collections"),
            callableName = Name.identifier("listOf"),
        )
        return referenceFunctions(callableId).firstOrNull { symbol ->
            val function = symbol.owner
            val params = function.nonDispatchParameters
            function.typeParameters.size == 1 &&
                params.size == 1 &&
                params[0].varargElementType != null
        }
    }
}
