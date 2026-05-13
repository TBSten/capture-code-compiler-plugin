package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.compat.k2000.filler.CaptureKindFillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.FillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.SourceFillerBuilder
import me.tbsten.capture.code.compat.k2000.filler.SourceLocationFillerBuilder
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
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
 * [K2000CapturedSourcesCollector] が収集した [CapturedSite] のリストから組み立てた
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
 * ## task-014 (ユーザ定義パラメータ) への引き継ぎ点
 *
 * 現状 [hasNonFillerRequiredParameters] が `true` を返す marker (= filler 以外の必須パラメータが
 * ある marker、例: `id: Id` 等) は書き換えをスキップする。task-014 はここで FIR 経由で得た
 * ユーザ定義パラメータ値を IR 化して `putValueArgument` する path を追加する想定。本 file 内
 * `buildSnippetsCall` の `// task-014:` コメント箇所が拡張ポイント。
 */
internal object K2000CapturedSourcesRewriter {

    /** 書き換え対象となる `capturedSources<T>()` の完全修飾名。 */
    const val CAPTURED_SOURCES_FQN: String = "me.tbsten.capture.code.capturedSources"

    /**
     * 与えられた [original] を [sites] から組み立てた `listOf<Marker>(...)` の [IrCall] に書き換える。
     *
     * [markerFqn] は `capturedSources<T>()` の type argument T (= 書き換え結果の list 要素型)。
     * `sites` は当該 marker でフィルタ済みの [CapturedSite] であることを呼び出し側が保証する。
     *
     * marker class が解決できない場合 (= runtime 依存が不足) は `null` を返し、
     * 呼び出し側は元の [IrCall] をそのまま返すこと。
     */
    fun rewriteCapturedSourcesCall(
        original: IrCall,
        markerFqn: String,
        sites: List<CapturedSite>,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ): IrCall? {
        val markerClassId = ClassId.topLevel(FqName(markerFqn))
        val markerSymbol = pluginContext.referenceClass(markerClassId) ?: return null
        val markerConstructor = markerSymbol.primaryConstructorOrNull() ?: return null

        // marker constructor の各 parameter について filler 型 (Source / SourceLocation / CaptureKind)
        // を識別し、対応する builder を resolve する。filler 型が使われていなければ resolve しない。
        val fillerPlan = buildFillerPlan(markerConstructor, pluginContext) ?: return null

        // task-014 で解消される制約:
        // filler 以外の必須 (no-default) パラメータがある marker は、ユーザ定義パラメータ値を IR 化する
        // logic がまだ無いため書き換えできない。書き換えをスキップし元の IrCall を返させる。
        if (markerConstructor.hasNonFillerRequiredParameters(fillerPlan)) return null

        val listOfSymbol = pluginContext.findListOfVararg() ?: return null
        val markerType = markerSymbol.defaultTypeProjection()
        val listElements = sites.map { site ->
            buildMarkerInstance(
                site = site,
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
                else -> { /* ユーザ定義パラメータ。task-014 で対応予定 */ }
            }
        }

        return FillerPlan(bindings)
    }

    /**
     * `Marker(...)` annotation instance を構築する。filler 型の parameter には対応する
     * [FillerBuilder] で生成した値を、それ以外はデフォルト値に委ねる (= putValueArgument しない)。
     */
    private fun buildMarkerInstance(
        site: CapturedSite,
        markerType: IrType,
        markerConstructor: IrConstructorSymbol,
        fillerPlan: FillerPlan,
        config: CaptureCodePluginConfig,
    ): IrExpression {
        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = markerType,
            constructorSymbol = markerConstructor,
        ).apply {
            fillerPlan.bindings.forEach { (index, builder) ->
                putValueArgument(index, builder.build(site, config))
            }
            // task-014: ここで filler 以外のユーザ定義パラメータ値を putValueArgument する path を追加する。
        }
    }

    private fun IrClassSymbol.primaryConstructorOrNull(): IrConstructorSymbol? =
        owner.constructors.firstOrNull { it.isPrimary }?.symbol
            ?: owner.constructors.firstOrNull()?.symbol

    /**
     * marker の primary constructor が、filler 以外で **必須 (no-default)** のパラメータを
     * 1 つでも持っていれば `true`。
     *
     * filler 型 (= [FillerPlan.bindings] に含まれる index) は plugin が値を自動で埋めるので、
     * default 値の有無に関わらず除外する。それ以外のパラメータが default 値を持たない場合のみ
     * `true` を返し、書き換えをスキップさせる (task-014 解消)。
     */
    private fun IrConstructorSymbol.hasNonFillerRequiredParameters(fillerPlan: FillerPlan): Boolean {
        return owner.valueParameters.withIndex().any { (index, parameter) ->
            index !in fillerPlan.bindings && !parameter.hasDefaultValue()
        }
    }

    private fun IrValueParameter.hasDefaultValue(): Boolean = defaultValue != null

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
