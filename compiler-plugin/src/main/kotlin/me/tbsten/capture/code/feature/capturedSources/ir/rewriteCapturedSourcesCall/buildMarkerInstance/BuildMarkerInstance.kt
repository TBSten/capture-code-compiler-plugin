package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteFailureWarnings
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.BuildFiller
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillCaptureKind
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillSource
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler.FillSourceLocation
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArg
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArgPrimitive
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import me.tbsten.capture.code.feature.markerDefinition.referenceMarkerClass
import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.text.MessageFormat

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
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall.invoke])
 * は以下を保証する責務がある。 違反時の挙動は 3 種類に分かれる:
 *
 * 1. **`require(...)` で fail-fast** — caller 責務として 100% 保証される値違反
 * 2. **task-137 で internal `error()` で fail-fast** — Kotlin spec / stdlib で保証され「絶対起きない」
 *    内部不変条件 (`primaryConstructorOrNull` null、 `listOf(vararg)` resolve fail)
 * 3. **task-135 で `CC_CAPTUREDSOURCES_REWRITE_FAILED` / `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND`
 *    warning + `null` 返却** — user 環境依存で起こりうる resolve fail (= marker class / filler class
 *    が runtime classpath に無い)
 *
 * - `markerFqn: String` は **non-blank** であること (= [require](kotlin.require) で fail-fast)。
 *   typical root cause: caller が空文字列を渡している (= [RewriteCapturedSourcesCall] の
 *   `markerFqnOf` は registered marker fqn のみ採用するため、 空文字列は来ないはず = caller bug)。
 * - `markerFqn` は [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   に登録済 (= FIR phase の `DiscoverMarkerClass` 経由)。 [referenceMarkerClass] で
 *   marker class symbol が解決可能 (nested marker の flatten FqN も分割候補の総当たりで
 *   解決)。 違反時は task-135 で `CC_CAPTUREDSOURCES_REWRITE_FAILED`
 *   warning + `null` 返却 (= user 環境依存)。
 * - marker class は `ANNOTATION_CLASS` で primary constructor を持つ (Kotlin spec で保証)。
 *   違反時は task-137 で internal `error()` で fail-fast (= plugin bug、 絶対起きない)。
 * - `pluginContext.referenceFunctions(listOf CallableId)` で `kotlin.collections.listOf(vararg)`
 *   が解決可能 (stdlib 必須)。 違反時は task-137 で internal `error()` で fail-fast (=
 *   stdlib 不在は環境破損、 絶対起きない)。
 * - filler class (`Source` / `SourceLocation` / `CaptureKind`) は `:annotation` runtime
 *   module で classpath に存在する。 違反時は task-135 で
 *   `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning + `null` 返却 (= user 環境依存)。
 * - `sites: List<CollectedSite>` の各 site の `markerFqn` は当該 `markerFqn` と等しい (= caller の
 *   `collectedSites.filter { it.site.markerFqn == markerFqn }` 結果)。 違反は signature 上の
 *   不変条件破りで silent (= 別 marker の filler 値が混ざる semantics fail だが require は重い)。
 * - `pluginContext.irBuiltIns.listClass` / `irBuiltIns.arrayClass` / `irBuiltIns.stringType` /
 *   `irBuiltIns.intType` は K2.0 〜 K2.4-RC 全 baseline で resolved (= stdlib 必須)。
 * - `compat: CompatContext` は IR primitive (`newIrCall` / `setCallTypeArgument` /
 *   `putCallValueArgument` / `newIrVararg` / `newIrConstructorCall` / `valueParametersOf` 等)
 *   の SPI が正しく dispatch される。
 * - `messageCollector: MessageCollector` は IR phase collector。 default [MessageCollector.NONE]
 *   は silent (既存 unit test 互換)。
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
     * @param messageCollector IR-phase [MessageCollector]。 task-135 で導入。 silent return null の
     *   経路 (marker resolve fail / filler resolve fail) を `CC_CAPTUREDSOURCES_REWRITE_FAILED` /
     *   `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning として user に通知する。 default の
     *   [MessageCollector.NONE] を渡せば silent (既存 unit test と非破壊な互換)。
     * @return `listOf<T>(T(...), ...)` を表す [IrExpression]、 resolve 失敗時は `null`
     */
    operator fun invoke(
        call: IrCall,
        markerFqn: String,
        sites: List<CollectedSite>,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        @Suppress("UNUSED_PARAMETER") config: CaptureCodePluginConfig,
        messageCollector: MessageCollector = MessageCollector.NONE,
    ): IrExpression? {
        // task-140: caller (= [RewriteCapturedSourcesCall]) は `markerFqnOf` で
        // `CaptureCodeMarkerRegistry.isMarker(fqn)` を pass した非空 fqn のみ渡す。
        // blank fqn が来るのは caller bug (= unit test の引数 typo 等)。
        require(markerFqn.isNotBlank()) {
            "BuildMarkerInstance: markerFqn must not be blank. " +
                "Typical root cause: caller (= RewriteCapturedSourcesCall) passed an empty " +
                "string or whitespace-only string, bypassing the registered-marker filter."
        }

        val markerSymbol = pluginContext.referenceMarkerClass(markerFqn)
            ?: run {
                reportWarning(messageCollector, RewriteFailureWarnings.REWRITE_FAILED, markerFqn)
                return null
            }
        // task-137: marker は事前に FIR phase で `@CaptureCode` メタ annotation 付きの
        // annotation class として validate 済 (Kotlin spec 上、 annotation class は primary
        // constructor を必ず持つ)。 そのため primary constructor の欠落は plugin 内部の
        // 不変条件破り (= bug) であり、 silent fallback ではなく fail-fast する。
        val markerConstructor = markerSymbol.primaryConstructorOrNull()
            ?: error(
                "Internal: marker class '$markerFqn' has no primary constructor; " +
                    "ANNOTATION_CLASS should always have a primary constructor (Kotlin spec). " +
                    "This is a compiler-plugin bug.",
            )
        val markerType = markerSymbol.typeWith()
        // task-137: `kotlin.collections.listOf(vararg)` は stdlib に必ず存在する SAM-less
        // top-level function であり、 plugin の動作対象環境 (= Kotlin compile path) に
        // stdlib が無い状況は想定外 (= 環境破損)。 silent fallback すると後段の IR 構築
        // で意味不明な NPE 等になりがちなので、 明示的に internal error として fail-fast。
        val listOfSymbol = pluginContext.findListOfVararg(compat)
            ?: error(
                "Internal: kotlin.collections.listOf(vararg) is not resolvable; " +
                    "ensure kotlin-stdlib is on the runtime classpath.",
            )

        val parameters = compat.valueParametersOf(markerConstructor.owner)
        // filler 型 dispatch table を 1 回だけ計算する (per-marker plan)。
        // null 戻り = 必要な filler class が runtime に無い → 本 marker は書き換え不能 → null。
        val fillerPlan = buildFillerPlan(parameters, pluginContext, markerFqn, messageCollector)
            ?: return null

        // 各 filler は symbol resolve を eager に行い、 marker FqN ごと 1 度だけ生成する。
        // resolve fail (runtime annotation 依存不足) は marker 全体 skip の trigger になる。
        // task-135: silent skip だった経路を `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning に
        // 昇格する。 3 つの filler のどれが欠けていても発火条件は同じ (= annotation runtime dep
        // 不足) なので emit は 1 度だけ。
        val fillSource = FillSource.resolveOrNull(pluginContext, compat)
            ?: return reportFillerNotFoundAndSkip(messageCollector, markerFqn)
        val fillSourceLocation = FillSourceLocation.resolveOrNull(pluginContext, compat)
            ?: return reportFillerNotFoundAndSkip(messageCollector, markerFqn)
        val fillCaptureKind = FillCaptureKind.resolveOrNull(pluginContext, compat)
            ?: return reportFillerNotFoundAndSkip(messageCollector, markerFqn)
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
                messageCollector = messageCollector,
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
            compat.newIrVararg(
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
     * 単数版 `capturedSource<T>()` 用の builder。 1 site 分の `IrConstructorCall` (= `T(...)`) を
     * 構築して返す。 caller は事前に site 件数 = 1 を保証してから呼ぶ責務 (0 件 / 複数件は IR phase の
     * `MessageCollector.report(ERROR, ...)` を発火して原 call を残す経路)。 resolve fail 時の挙動は
     * [invoke] と完全に同じ (marker / filler 不在 → 該当 warning + `null` 返却)。
     *
     * @param markerFqn 書き換え対象の marker class FQN
     * @param site 当該 marker でフィルタ済の **唯一** の site
     * @param pluginContext IrPluginContext
     * @param compat IR primitive 委譲 SPI
     * @param config global Gradle DSL config (現状は [invoke] と signature 整合のため受け取るだけ)
     * @param messageCollector IR-phase MessageCollector
     * @return `T(...)` を表す [IrConstructorCall]、 resolve 失敗時は `null`
     */
    internal fun buildOneInstance(
        markerFqn: String,
        site: CollectedSite,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        @Suppress("UNUSED_PARAMETER") config: CaptureCodePluginConfig,
        messageCollector: MessageCollector = MessageCollector.NONE,
    ): IrConstructorCall? {
        require(markerFqn.isNotBlank()) {
            // 文面は invoke 側と意図的に違える: Charter8RequireTripProbeTest が constant pool 文字列で
            // BuildMarkerInstance の require を 2 か所に分けて区別するため (= 「ちょうど 1 つ」 invariant
            // 保護)。 invariant 自体は invoke 側と同じ。
            "BuildMarkerInstance.buildOneInstance: markerFqn is blank. " +
                "Typical root cause: caller (= RewriteCapturedSourceCall) passed an empty string, " +
                "bypassing the registered-marker filter."
        }

        val markerSymbol = pluginContext.referenceMarkerClass(markerFqn)
            ?: run {
                reportWarning(messageCollector, RewriteFailureWarnings.REWRITE_FAILED, markerFqn)
                return null
            }
        val markerConstructor = markerSymbol.primaryConstructorOrNull()
            ?: error(
                "Internal: marker class '$markerFqn' has no primary constructor; " +
                    "ANNOTATION_CLASS should always have a primary constructor (Kotlin spec). " +
                    "This is a compiler-plugin bug.",
            )
        val markerType = markerSymbol.typeWith()

        val parameters = compat.valueParametersOf(markerConstructor.owner)
        val fillerPlan = buildFillerPlan(parameters, pluginContext, markerFqn, messageCollector)
            ?: return null

        val fillSource = FillSource.resolveOrNull(pluginContext, compat)
            ?: run {
                reportWarning(messageCollector, RewriteFailureWarnings.FILLER_NOT_FOUND, markerFqn)
                return null
            }
        val fillSourceLocation = FillSourceLocation.resolveOrNull(pluginContext, compat)
            ?: run {
                reportWarning(messageCollector, RewriteFailureWarnings.FILLER_NOT_FOUND, markerFqn)
                return null
            }
        val fillCaptureKind = FillCaptureKind.resolveOrNull(pluginContext, compat)
            ?: run {
                reportWarning(messageCollector, RewriteFailureWarnings.FILLER_NOT_FOUND, markerFqn)
                return null
            }
        val buildUserArg = BuildUserArg()
        val buildUserArgPrimitive = BuildUserArgPrimitive()

        return buildSingle(
            markerType = markerType,
            markerConstructor = markerConstructor,
            parameters = parameters,
            fillerPlan = fillerPlan,
            site = site,
            pluginContext = pluginContext,
            compat = compat,
            fillSource = fillSource,
            fillSourceLocation = fillSourceLocation,
            fillCaptureKind = fillCaptureKind,
            buildUserArg = buildUserArg,
            buildUserArgPrimitive = buildUserArgPrimitive,
            messageCollector = messageCollector,
        )
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
     *
     * ## Preconditions
     *
     * Caller (= [invoke]) は以下を保証する責務がある。
     *
     * - `parameters` は marker primary constructor の value parameters (= caller の
     *   `compat.valueParametersOf(markerConstructor.owner)` 結果)。 EXPRESSION 起源で
     *   markerCall == null、 declaration / file 起源で markerCall != null の不変条件は
     *   [CollectedSite] の data class フィールドで保証 ([CollectDeclarationSite] の各経路
     *   で markerCall を set/unset)。
     * - `fillerPlan.bindings` の各 key は `parameters.indices` 内 (= caller の `buildFillerPlan`
     *   が `forEachIndexed` で構築する不変条件)。 違反時は invoke 冒頭の `require(...)` で
     *   fail-fast (= typical root cause: `buildFillerPlan` が想定外の index を含む plan を返した、
     *   = plugin bug)。
     * - `fillSource` / `fillSourceLocation` / `fillCaptureKind` は caller が `resolveOrNull`
     *   pass 済 (= non-null filler 実装) で渡す。 violations は signature 上不可能。
     * - `buildUserArg` / `buildUserArgPrimitive` は state なし factory なので reuse 可。
     * - `site.markerCall == null ⇔ site.kind == EXPRESSION` (= [CollectedSite] data class の
     *   不変条件)。 declaration / file 起源は markerCall が非 null。 違反は signature 上不可だが、
     *   `markerCall == null` 経路では [BuildUserArgPrimitive] → `defaultValue?.expression` の
     *   2 段 fallback で安全に動作する。
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
        messageCollector: MessageCollector,
    ): IrConstructorCall {
        // task-140: fillerPlan は caller の `buildFillerPlan` が `parameters.forEachIndexed` で
        // 構築するため、 全 key は `parameters.indices` 内 (= 不変条件)。 万一外れた場合は
        // `parameters[index]` で IndexOutOfBoundsException になる前に明示 fail-fast する。
        // typical root cause: buildFillerPlan の構築 logic が誤って `parameters.size` 以上の
        // index を登録した plugin bug (= caller の不変条件破り)。
        require(fillerPlan.bindings.keys.all { it in parameters.indices }) {
            "BuildMarkerInstance.buildSingle: fillerPlan.bindings has keys out of range " +
                "(0 until ${parameters.size}): ${fillerPlan.bindings.keys}. " +
                "Typical root cause: buildFillerPlan registered an index past the parameter list, " +
                "which is a compiler-plugin bug."
        }

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
                    //
                    // task-134: EXPRESSION 起源で BuildUserArgPrimitive が enum/class ref を
                    // 解決できなかった場合は `CC_USERARG_ENUM_NOT_FOUND` /
                    // `CC_USERARG_CLASS_REF_UNSUPPORTED` warning を発火し、 caller には
                    // 引き続き null を返して default 値 fallback を行わせる。 messageCollector を
                    // BuildUserArgPrimitive に forward することで、 warning emit と null fallback
                    // を 1 経路で完結させる (= 既存 unit test は MessageCollector.NONE で silent)。
                    val markerCall = site.markerCall
                    if (markerCall != null) {
                        buildUserArg(markerCall, index, parameter, compat)
                    } else {
                        val name = parameter.name.asString()
                        val pushed = site.expressionUserArgs[name]
                        buildUserArgPrimitive(pushed, parameter, pluginContext, compat, messageCollector)
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
     * そのまま残す (= silent skip)。 task-135 で silent skip 経路を
     * `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning として [messageCollector] に通知するようにし、
     * その後 `null` を返す挙動は維持する。
     */
    private fun buildFillerPlan(
        parameters: List<org.jetbrains.kotlin.ir.declarations.IrValueParameter>,
        pluginContext: IrPluginContext,
        markerFqn: String,
        messageCollector: MessageCollector,
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
            if (pluginContext.referenceClass(fillerClassId) == null) {
                reportWarning(
                    messageCollector,
                    RewriteFailureWarnings.FILLER_NOT_FOUND,
                    markerFqn,
                )
                return null
            }

            bindings[index] = fillerKind
        }
        return FillerPlan(bindings)
    }

    /**
     * task-135 helper: emit [warning] (1 String 引数) via [messageCollector]。
     * `MessageCollector.report(...)` の bytecode は K2.0 .. K2.4-RC で identical で、
     * 同 pattern を [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound.WarnIfNoMarkerFound]
     * が既に採用しているため main 側に閉じた helper として再利用しやすい。
     *
     * location は `null` を渡す (= `transformCallsInModule` 内では [org.jetbrains.kotlin.ir.declarations.IrFile]
     * を直接保持しないため)。 warning message body の marker FqN で対象が一意に特定できる。
     */
    private fun reportWarning(
        messageCollector: MessageCollector,
        warning: CaptureCodeCompilerPluginWarning,
        markerFqn: String,
    ) {
        if (messageCollector === MessageCollector.NONE) return
        val text = MessageFormat.format(warning.message, markerFqn)
        messageCollector.report(CompilerMessageSeverity.WARNING, text, null)
    }

    /**
     * `FILLER_NOT_FOUND` を 1 度発火しつつ `null` を返す convenience。 invoke の `?: run { ... }`
     * を `?: return reportFillerNotFoundAndSkip(...)` の 1 行に詰めるため。
     */
    private fun reportFillerNotFoundAndSkip(
        messageCollector: MessageCollector,
        markerFqn: String,
    ): IrExpression? {
        reportWarning(messageCollector, RewriteFailureWarnings.FILLER_NOT_FOUND, markerFqn)
        return null
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
     *
     * value parameter の取り出しは [CompatContext.valueParametersOf] 経由で行う。 K2.4-RC では
     * `IrFunction.valueParameters` が削除され `nonDispatchParameters` にリネームされた (drift
     * D-IR-3/D-IR-33) ため、 直接呼ぶと `NoSuchMethodError` が発生する。
     */
    private fun IrPluginContext.findListOfVararg(compat: CompatContext): IrSimpleFunctionSymbol? {
        val callableId = CallableId(
            packageName = FqName("kotlin.collections"),
            callableName = Name.identifier("listOf"),
        )
        return referenceFunctions(callableId).firstOrNull { symbol ->
            val function = symbol.owner
            if (function.typeParameters.size != 1) return@firstOrNull false
            val params = compat.valueParametersOf(function)
            params.size == 1 && params[0].varargElementType != null
        }
    }
}
