package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.k2000.filler.CaptureKindFillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.FillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.SourceFillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.SourceLocationFillerBuilder
import me.tbsten.capture.code.compat.k2000.userargs.UserArgIrBuilder
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Logic H (`capturedSources<T>()` 書き換え) の Kotlin 2.0.0 向け実装。
 *
 * 入力された [IrCall] (= `me.tbsten.capture.code.capturedSources<Marker>()`、`Marker` は
 * [me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry] に登録された marker のいずれか) を、
 * [K2000CapturedSourcesCollector] が収集した [K2000CapturedSiteData] のリストから組み立てた
 * `listOf(Marker(...))` 相当の [IrCall] (`kotlin.collections.listOf`) に書き換える。
 *
 * ## task-013 で対応した変更点
 *
 * - **filler 3 種すべての自動値埋め**: 旧版は `source: Source` のみ inline で構築していたが、
 *   `source` / `location: SourceLocation` / `kind: CaptureKind` の filler 3 種を統一的に扱う
 *   ように `filler/` パッケージ配下の builder クラス群に切り出した。`buildSnippetsCall` は
 *   marker constructor の各 parameter を順に走査し、その type が filler 型に該当すれば対応する
 *   [FillerBuilder] に dispatch する。
 * - **filler 識別は型ベース**: ユーザ定義パラメータ (task-014) との境界はここで明確にしている。
 *   parameter の `type` が `me.tbsten.capture.code.{Source, SourceLocation, CaptureKind}` の
 *   いずれかと **等しい** 場合のみ filler、それ以外はユーザ定義扱い。design §3.2 / §3.3。
 * - **SourceNormalizer の wire up**: 生 source 取得は [K2000CapturedSourcesCollector] 内で
 *   [me.tbsten.capture.code.feature.captured_sources.normalize.normalize] を経由するようになった
 *   (task-015 ↔ task-013 の合流地点)。本 rewriter は normalize 済の `site.source` をそのまま使う。
 *
 * ## task-014 で追加されたユーザ定義パラメータの保持
 *
 * filler ではない parameter (= ユーザが call site で値を指定する parameter、例 `id: Id` /
 * `label: String` / `target: KClass<*>` 等) について、collector が保持した marker `IrConstructorCall`
 * から `getValueArgument(i)` で IR 式を取り出し、[UserArgIrBuilder] が deepCopy して新 marker
 * instance に再投入する。call site で省略されている場合は marker class の primary constructor の
 * `IrValueParameter.defaultValue` を使う。design §5 Logic H / §7.9 / 開発タスク順序 #4。
 */
internal object K2000CapturedSourcesRewriter {

    /** 書き換え対象となる `capturedSources<T>()` の完全修飾名。 */
    const val CAPTURED_SOURCES_FQN: String = "me.tbsten.capture.code.capturedSources"

    /**
     * 与えられた [original] を [siteData] から組み立てた `listOf<Marker>(...)` の [IrCall] に書き換える。
     *
     * [markerFqn] は `capturedSources<T>()` の type argument T (= 書き換え結果の list 要素型)。
     * `siteData` は当該 marker でフィルタ済みであることを呼び出し側が保証する。
     *
     * marker class が解決できない場合 (= runtime 依存が不足) は `null` を返し、
     * 呼び出し側は元の [IrCall] をそのまま返すこと。
     */
    fun rewriteCapturedSourcesCall(
        original: IrCall,
        markerFqn: String,
        siteData: List<K2000CapturedSiteData>,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ): IrCall? {
        val markerClassId = ClassId.topLevel(FqName(markerFqn))
        val markerSymbol = pluginContext.referenceClass(markerClassId) ?: return null
        val markerConstructor = markerSymbol.primaryConstructorOrNull() ?: return null

        // marker constructor の各 parameter について filler 型 (Source / SourceLocation / CaptureKind)
        // を識別し、対応する builder を resolve する。filler 型が使われていなければ resolve しない。
        val fillerPlan = buildFillerPlan(markerConstructor, pluginContext) ?: return null

        val listOfSymbol = pluginContext.findListOfVararg() ?: return null
        val markerType = markerSymbol.defaultTypeProjection()
        val listElements = siteData.map { data ->
            buildMarkerInstance(
                data = data,
                markerType = markerType,
                markerConstructor = markerConstructor,
                fillerPlan = fillerPlan,
                config = config,
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
            valueArgumentsCount = 1,
        ).apply {
            putTypeArgument(0, markerType)
            putValueArgument(
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

    /**
     * marker constructor の各 parameter index と filler builder の対応表 + 必要な builder の resolve 結果。
     *
     * filler 型ではない parameter は本 map に含まれない (= 後段でユーザ定義 / no-default 検査の対象)。
     */
    private class FillerPlan(
        val bindings: Map<Int, FillerBuilder>,
    )

    /**
     * marker constructor の value parameter を走査し、filler 型に該当するものに対する
     * [FillerBuilder] を resolve した [FillerPlan] を返す。
     *
     * いずれかの builder の resolve が失敗 (runtime 依存不足) した場合は `null`。
     * filler が 1 つも無い marker (= ケース #8 の Bench) は空 map を持つ [FillerPlan] を返す。
     */
    private fun buildFillerPlan(
        markerConstructor: IrConstructorSymbol,
        pluginContext: IrPluginContext,
    ): FillerPlan? {
        // filler 型を FqName 文字列レベルで識別する (IrClass.classId は安定 API ではないため、
        // classFqName 経由で文字列化して [CaptureCodeFillerClassIds] と比較する形を取る)。
        val sourceFqn = CaptureCodeFillerClassIds.Source.asFqNameString()
        val locationFqn = CaptureCodeFillerClassIds.SourceLocation.asFqNameString()
        val captureKindFqn = CaptureCodeFillerClassIds.CaptureKind.asFqNameString()

        val parameters = markerConstructor.owner.valueParameters
        val bindings = mutableMapOf<Int, FillerBuilder>()

        // 1 種類でも該当 parameter があれば builder を resolve する (lazy 化)。
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
                    // ユーザ定義パラメータ。`UserArgIrBuilder` で別経路で値を埋める (task-014)。
                }
            }
        }

        return FillerPlan(bindings)
    }

    /**
     * `Marker(...)` annotation instance を構築する。
     *
     * marker constructor の各 parameter について:
     * - filler 型 (= [FillerPlan.bindings] に含まれる index) → [FillerBuilder] で値を生成
     * - それ以外 (ユーザ定義) → [UserArgIrBuilder] で call site 値 or default 値を deepCopy
     *
     * task-014 でユーザ定義 parameter サポートを追加。それ以前は filler 以外を default 値に任せる
     * (`putValueArgument` しない) 形だったが、Kotlin の primary constructor は **value argument を
     * 全部 explicit に指定しなければ instance を生成できない** ことが分かったため、
     * 全 parameter を埋めるよう変更。
     */
    private fun buildMarkerInstance(
        data: K2000CapturedSiteData,
        markerType: IrType,
        markerConstructor: IrConstructorSymbol,
        fillerPlan: FillerPlan,
        config: CaptureCodePluginConfig,
    ): IrExpression {
        val markerCall = data.markerCall
        val site = data.site
        val parameters = markerConstructor.owner.valueParameters

        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = markerType,
            constructorSymbol = markerConstructor,
        ).apply {
            parameters.forEachIndexed { index, parameter ->
                val fillerBuilder = fillerPlan.bindings[index]
                if (fillerBuilder != null) {
                    // filler: plugin が自動で値を埋める (ユーザの call site 指定があっても上書き)
                    putValueArgument(index, fillerBuilder.build(site, config))
                } else {
                    // ユーザ定義 parameter: call site の指定値 or default 値を deepCopy で詰める
                    val userExpr = UserArgIrBuilder.buildOrDefault(markerCall, index, parameter)
                    if (userExpr != null) {
                        putValueArgument(index, userExpr)
                    }
                    // userExpr が null = required parameter が省略されているケース (本来 compile
                    // error)。`putValueArgument(index, null)` のまま進む。FIR checker が事前に
                    // 弾く想定だが、念のため例外を投げず null pass する。
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
     * `listOf` には複数オーバーロード (`()`, `(T)`, `(vararg T)`) があるため、type parameter が 1 つで
     * かつ value parameter が 1 つ (= vararg) のものを採用する。
     */
    private fun IrPluginContext.findListOfVararg(): IrSimpleFunctionSymbol? {
        val callableId = CallableId(
            packageName = FqName("kotlin.collections"),
            callableName = Name.identifier("listOf"),
        )
        return referenceFunctions(callableId).firstOrNull { symbol ->
            val function = symbol.owner
            function.typeParameters.size == 1 &&
                function.valueParameters.size == 1 &&
                function.valueParameters[0].varargElementType != null
        }
    }
}
