package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.BuildFiller
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillCaptureKind
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillSource
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillSourceLocation
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArg
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArgPrimitive
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
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
 * Logic H sub-step: marker FqN ごとに `listOf(T(...), T(...), ...)` の IR を構築する logic。
 *
 * task-120-B Phase 4a で concrete 化。 既存 `K{XXX}CapturedSourcesRewriter` 配下の
 * `rewriteCapturedSourcesCall` / `buildFillerPlan` / `buildMarkerInstance` の 3 method を
 * main module 1 箇所に集約した版。 IR 構築本体は [CompatContext] の Phase 2 で追加した
 * `newIrCall` / `newIrConstructorCall` / `setCallTypeArgument` / `putCallValueArgument` /
 * `valueParametersOf` 経由で drift 吸収する。
 *
 * ## 責務
 *
 * - [invoke] が 1 marker FqN + その site 群を受け取り、 `listOf(T(site1), T(site2), ...)` の
 *   IR を構築して返す
 * - marker class / constructor / `listOf(vararg)` の symbol resolve は本 class 内 で 1 回だけ行う
 * - 各 site について [buildSingle] が `T(...)` の `IrConstructorCall` を組み立てる
 * - constructor の各 parameter について filler 型 (Source / SourceLocation / CaptureKind) は
 *   [BuildFiller] (Phase 4b で concrete 化) に dispatch、 それ以外は [BuildUserArg] /
 *   [BuildUserArgPrimitive] (Phase 4b) に dispatch
 *
 * ## Phase 4b 完了後の状態
 *
 * filler / userarg の concrete impl は Phase 4b で main 側に移植済 ([FillSource] / [FillSourceLocation]
 * / [FillCaptureKind] / [BuildUserArg] / [BuildUserArgPrimitive])。 本 class + chain は full
 * functional だが、 [RewriteCapturedSourcesCall] の caller (= main 側
 * `CaptureCodeIrExtension`) はまだ wire されていない (= Phase 5 で実施) ため、 既存 test は引き続き
 * `compat-kXXX/K{XXX}CapturedSourcesRewriter` 経路で PASS する。
 *
 * ## 旧構造との関係
 *
 * 既存 `K{XXX}CapturedSourcesRewriter.rewriteCapturedSourcesCall` は引き続き runtime path として
 * 残り、 既存 test は compat-kXXX 経路で PASS する。 Phase 5 で `transformIr` を main 経由に
 * 切り替えた時点で本 class が runtime path になり、 Phase 6 で旧 rewriter 削除。
 */
internal class BuildMarkerInstance {

    /**
     * 1 marker FqN とその全 site から `listOf(T(site1), T(site2), ...)` の IR 式を構築する。
     *
     * marker symbol が resolve 不能 (= runtime 依存不足) / `listOf(vararg)` symbol が解決できない
     * / filler 型が宣言されているが対応 filler class が classpath に無い、 などの場合は `null` を
     * 返し、 [RewriteCapturedSourcesCall] は原 call をそのまま残す (= stub の `listOf()` が返る)。
     *
     * @param call 書き換え対象の `capturedSources<T>()` 呼び出し (offset / element type の参照元)
     * @param markerFqn 書き換え対象の marker class FQN (= T)
     * @param sites 当該 marker でフィルタ済の site 群 (発見順)。 空の場合は `listOf()` (空 list) を構築
     * @param pluginContext IrPluginContext (class / function symbol 解決用)
     * @param compat IR primitive を委譲する SPI
     * @param config global Gradle DSL config (per-site effective config は [CollectedSite] が保持)
     * @return `listOf<T>(T(...), ...)` を表す [IrExpression]、 resolve 失敗時は `null`
     */
    operator fun invoke(
        call: IrCall,
        markerFqn: String,
        sites: List<CollectedSite>,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        @Suppress("UNUSED_PARAMETER") config: CaptureCodePluginConfig,
    ): IrExpression? {
        val markerSymbol = pluginContext.referenceClass(ClassId.topLevel(FqName(markerFqn)))
            ?: return null
        val markerConstructor = markerSymbol.primaryConstructorOrNull() ?: return null
        val markerType = markerSymbol.typeWith()
        val listOfSymbol = pluginContext.findListOfVararg() ?: return null

        val parameters = compat.valueParametersOf(markerConstructor.owner)
        // filler 型 dispatch table を 1 回だけ計算する (per-marker plan)。
        // null 戻り = 必要な filler class が runtime に無い → 本 marker は書き換え不能 → null。
        val fillerPlan = buildFillerPlan(parameters, pluginContext) ?: return null

        // 各 filler は symbol resolve を eager に行い、 marker FqN ごと 1 度だけ生成する。
        // resolve fail (runtime annotation 依存不足) は marker 全体 skip の trigger になる。
        val fillSource = FillSource.resolveOrNull(pluginContext, compat) ?: return null
        val fillSourceLocation = FillSourceLocation.resolveOrNull(pluginContext, compat) ?: return null
        val fillCaptureKind = FillCaptureKind.resolveOrNull(pluginContext, compat) ?: return null
        val buildUserArg = BuildUserArg()
        val buildUserArgPrimitive = BuildUserArgPrimitive()

        val listElements = sites.map { collected ->
            buildSingle(
                markerType = markerType,
                markerConstructor = markerConstructor,
                parameters = parameters,
                fillerPlan = fillerPlan,
                site = collected,
                pluginContext = pluginContext,
                compat = compat,
                fillSource = fillSource,
                fillSourceLocation = fillSourceLocation,
                fillCaptureKind = fillCaptureKind,
                buildUserArg = buildUserArg,
                buildUserArgPrimitive = buildUserArgPrimitive,
            )
        }

        val listType = pluginContext.irBuiltIns.listClass.typeWith(markerType)
        val varargType = pluginContext.irBuiltIns.arrayClass.typeWith(markerType)
        val listCall = compat.newIrCall(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = listType,
            symbol = listOfSymbol,
            typeArgumentsCount = 1,
        )
        compat.setCallTypeArgument(listCall, 0, markerType)
        compat.putCallValueArgument(
            listCall,
            0,
            IrVarargImpl(
                startOffset = call.startOffset,
                endOffset = call.endOffset,
                type = varargType,
                varargElementType = markerType,
                elements = listElements.toList<IrVarargElement>(),
            ),
        )
        return listCall
    }

    /**
     * marker constructor 1 つ分の `IrConstructorCall` (= `T(...)`) を組み立てる。
     *
     * constructor の各 parameter について:
     * - filler 型 (= [fillerPlan] に index で登録されている) → 対応する [BuildFiller] で値を生成
     * - filler 型ではない (= ユーザ定義 parameter) → markerCall (= declaration / file 起源で
     *   non-null) があれば [BuildUserArg] で deepCopy、 null なら [BuildUserArgPrimitive] で
     *   `expressionUserArgs` から IR const 再構築、 それも null なら default 値 (= constructor の
     *   `defaultValue?.expression`) を deepCopy
     *
     * 各 parameter の argument が `null` のままになるケースは constructor の primary parameter が
     * 省略されているケース (= 本来 compile error)。 putCallValueArgument は値が null でも呼ばず、
     * primary constructor の default で fill される pattern とする (= 既存 K200 と同等)。
     */
    private fun buildSingle(
        markerType: IrType,
        markerConstructor: IrConstructorSymbol,
        parameters: List<org.jetbrains.kotlin.ir.declarations.IrValueParameter>,
        fillerPlan: FillerPlan,
        site: CollectedSite,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        fillSource: FillSource,
        fillSourceLocation: FillSourceLocation,
        fillCaptureKind: FillCaptureKind,
        buildUserArg: BuildUserArg,
        buildUserArgPrimitive: BuildUserArgPrimitive,
    ): IrConstructorCall {
        val ctorCall = compat.newIrConstructorCall(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = markerType,
            constructorSymbol = markerConstructor,
        )
        parameters.forEachIndexed { index, parameter ->
            val fillerKind = fillerPlan.bindings[index]
            val argExpr: IrExpression? = when (fillerKind) {
                FillerKind.SOURCE -> fillSource(site.site, site.effectiveConfig)
                FillerKind.SOURCE_LOCATION -> fillSourceLocation(site.site, site.effectiveConfig)
                FillerKind.CAPTURE_KIND -> fillCaptureKind(site.site, site.effectiveConfig)
                null -> {
                    // ユーザ定義 parameter:
                    //  - declaration / file 起源 (markerCall != null) → BuildUserArg で deepCopy
                    //  - EXPRESSION 起源 (markerCall == null) → まず BuildUserArgPrimitive で IR
                    //    const 再構築を試み、 それでも null なら BuildUserArg の default 値経路
                    val markerCall = site.markerCall
                    if (markerCall != null) {
                        buildUserArg(markerCall, index, parameter, compat)
                    } else {
                        val name = parameter.name.asString()
                        val pushed = site.expressionUserArgs[name]
                        buildUserArgPrimitive(pushed, parameter, pluginContext)
                            ?: buildUserArg(null, index, parameter, compat)
                    }
                }
            }
            if (argExpr != null) {
                compat.putCallValueArgument(ctorCall, index, argExpr)
            }
        }
        return ctorCall
    }

    /**
     * marker constructor の value parameter を走査して filler 型 dispatch table を作る。
     *
     * - parameter の type が `Source` / `SourceLocation` / `CaptureKind` のいずれかの場合のみ
     *   [FillerKind] を登録する (= ユーザ定義 parameter は本 plan に含まれない)
     * - filler 型 parameter が 1 つもない marker (例: ケース #8 Bench) は空 bindings の plan を返す
     *
     * IR drift 吸収のため、 必要な filler class が `pluginContext.referenceClass(...)` で resolve
     * 不能の場合は plan の構築自体が `null` を返し、 [RewriteCapturedSourcesCall] が原 call を
     * そのまま残す (= silent skip)。
     */
    private fun buildFillerPlan(
        parameters: List<org.jetbrains.kotlin.ir.declarations.IrValueParameter>,
        pluginContext: IrPluginContext,
    ): FillerPlan? {
        val sourceFqn = CaptureCodeFillerClassIds.Source.asFqNameString()
        val locationFqn = CaptureCodeFillerClassIds.SourceLocation.asFqNameString()
        val kindFqn = CaptureCodeFillerClassIds.CaptureKind.asFqNameString()
        val bindings = mutableMapOf<Int, FillerKind>()

        parameters.forEachIndexed { index, param ->
            val paramTypeFqn = param.type.classFqName?.asString()
            val fillerKind = when (paramTypeFqn) {
                sourceFqn -> FillerKind.SOURCE
                locationFqn -> FillerKind.SOURCE_LOCATION
                kindFqn -> FillerKind.CAPTURE_KIND
                else -> null
            } ?: return@forEachIndexed

            // 該当 filler class が runtime classpath に存在することを resolve で確認する
            // (resolve 失敗 = runtime 依存不足 → 書き換え不能のため plan 全体を null で返す)。
            val fillerClassId = when (fillerKind) {
                FillerKind.SOURCE -> CaptureCodeFillerClassIds.Source
                FillerKind.SOURCE_LOCATION -> CaptureCodeFillerClassIds.SourceLocation
                FillerKind.CAPTURE_KIND -> CaptureCodeFillerClassIds.CaptureKind
            }
            pluginContext.referenceClass(fillerClassId) ?: return null

            bindings[index] = fillerKind
        }
        return FillerPlan(bindings)
    }

    /**
     * filler 型 dispatch table。 marker constructor の parameter index → どの filler を呼ぶか。
     *
     * Phase 4a 段階では filler 種別の列挙のみで、 具体的な IR 構築は [BuildFiller] sub-class
     * (= Phase 4b で concrete 化) に dispatch する。
     */
    private class FillerPlan(
        val bindings: Map<Int, FillerKind>,
    )

    private enum class FillerKind { SOURCE, SOURCE_LOCATION, CAPTURE_KIND }

    private fun IrClassSymbol.primaryConstructorOrNull(): IrConstructorSymbol? =
        owner.constructors.firstOrNull { it.isPrimary }?.symbol
            ?: owner.constructors.firstOrNull()?.symbol

    /**
     * `kotlin.collections.listOf(vararg elements: T): List<T>` の symbol を解決する。
     *
     * `listOf` には複数オーバーロード (`()`, `(T)`, `(vararg T)`) があるため、 type parameter が
     * 1 つでかつ value parameter が 1 つ (= vararg) のものを採用する。
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
