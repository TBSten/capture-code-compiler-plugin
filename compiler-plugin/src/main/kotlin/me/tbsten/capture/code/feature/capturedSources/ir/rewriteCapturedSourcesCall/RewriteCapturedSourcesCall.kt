package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound.WarnIfNoMarkerFound
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import java.text.MessageFormat

/**
 * Logic H: `capturedSources<T>()` 呼び出しを `listOf(T(...))` に書き換える logic。
 *
 * task-120-B Phase 4a で concrete 化。 これまで各 `compat-kXXX/K{XXX}CapturedSourcesRewriter.kt`
 * + `K{XXX}IrTransform.kt` (transformer 部分) に重複していた 「`capturedSources<T>()` 検出 →
 * marker FqN 抽出 → site 集約 → marker instance 列構築」 の orchestrator 部分を main module 1
 * 箇所に集約した版。 IR 構築本体 (= IR drift) は [CompatContext] の Phase 2 で追加した 11 IR
 * primitive 経由で吸収する。
 *
 * ## 責務
 *
 * - [invoke] が module 全体を走査して `capturedSources<T>()` の各 [IrCall] を transform
 * - call が `me.tbsten.capture.code.capturedSources` (CallableId 一致) かつ T 型引数が
 *   registered marker の場合のみ [BuildMarkerInstance] に dispatch して `listOf(T(...))` で
 *   置換する
 * - T が registered marker ではない (= 未登録の type argument) 場合:
 *     - T の class が `@CaptureCode` meta annotation を持つ (= marker のはずなのに declaration が
 *       今回の compilation unit に含まれていない) なら `CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED`
 *       を **compile ERROR** として report し、 rewrite しない (bug-001)。 典型原因は stale な
 *       incremental build か、 marker を別 module / compilation に置いた構成。 silent skip のままだと
 *       runtime stub が class file に残って実行時に `IllegalStateException` になるため error に格上げ
 *     - meta annotation を持たない T は FIR phase (Logic G,
 *       `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE`) が既に error 済の経路なので no-op (元の call を
 *       そのまま残す)
 * - module 全体 walk は [CompatContext.transformCallsInModule] (Phase 2 SPI) に委譲し、 IR
 *   transformer 基底 class の drift を吸収する
 *
 * ## 旧構造との関係 (Phase 4a 時点)
 *
 * 既存の `K{XXX}CapturedSourcesRewriter` + `K{XXX}IrTransform` の transformer 部分は **並行存在**
 * する。 Phase 5 で `transformIr` 経由の wiring を main 経由に切り替えるまで、 既存 path が runtime
 * path として残り続け、 本 class は caller 0 件 (= dead code) のまま。 既存 test は引き続き compat-kXXX
 * 経路で PASS する想定。 Phase 6 で各 compat-kXXX の旧 transformer を削除する。
 *
 * ## task-120-B Phase 7 update (warning emission)
 *
 * [invoke] は `messageCollector` (default = [MessageCollector.NONE]) を受け取り、
 * `config.warnOnEmptyCapture == true` && `capturedSources<T>()` の T が 0 site の場合に
 * [WarnIfNoMarkerFound] を経由して `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warning を 1 marker
 * FqN あたり 1 度だけ発火する。 既存 caller (`CaptureCodeIrExtension`) は
 * [me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder] 経由で collector を取得して
 * 渡す。 collector を渡さなければ silent (= 既存 unit test と非破壊な互換)。
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 [BuildMarkerInstance] と組み合わせて使うため、 invoke は state を
 * 持たない pure method として呼べる (`RewriteCapturedSourcesCall()(...)`)。
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate]) は以下を保証する責務がある。
 *
 * - `moduleFragment: IrModuleFragment` は IR phase で plugin context に渡される引数 (signature 上保証)。
 * - `pluginContext: IrPluginContext` は IR phase で resolve 済。 [BuildMarkerInstance] が
 *   marker class / constructor / `listOf(vararg)` symbol を解決する経路で必要。 stdlib /
 *   `:annotation` runtime dependency が missing なら fail-fast (task-137 で internal error 化済)
 *   もしくは warning + silent skip (task-135 で `CC_CAPTUREDSOURCES_REWRITE_FAILED` /
 *   `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` 化済)。
 * - `compat: CompatContext` は同 module の `CompatContextImpl` actual 実装で、
 *   `transformCallsInModule` (IR transformer base class drift)、 `getCallTypeArgument` (K2.4-RC
 *   削除 API drift)、 `newIrCall` / `setCallTypeArgument` / `putCallValueArgument` /
 *   `newIrVararg` 等の SPI が正しく dispatch される。
 * - `config: CaptureCodePluginConfig` は `CaptureCodePluginConfigHolder` 経由で publish された
 *   global config。 `warnOnEmptyCapture` 等の DSL option を保持。
 * - `collectedSites: List<CollectedSite>` は [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite]
 *   の戻り値である必要がある (= 各 site が `markerFqn != ""` の不変条件を満たす)。 違反時は invoke
 *   冒頭の `require(...)` で fail-fast (= typical root cause: caller が手動構築した invalid
 *   `CollectedSite` を渡している、 もしくは [CollectDeclarationSite] の戻り値が後段で mutate
 *   されている)。
 * - `messageCollector: MessageCollector` は IR phase の collector。 default [MessageCollector.NONE]
 *   は silent (既存 unit test 互換) で safe。 typical root cause: holder の `compute()` が呼ばれる
 *   前に invoke された (= phase 順序 bug) — silent fallback は `MessageCollector.NONE` と同等動作。
 * - call が `capturedSources<T>()` でない場合 (= [isCapturedSourcesCall] false)、 transformer
 *   は `null` を返して call を変更しない (= silent skip)。
 * - `compat.getCallTypeArgument(call, 0)` が `null` を返すケース (= type argument 0 件) は
 *   silent skip。 task-139 で FIR phase の `ValidateCapturedSourcesCall` が `typeArguments.isNotEmpty()`
 *   を `require(...)` で保証済なので、 IR phase でここに到達するのは silent compile error path
 *   (= 既に別 error が user に出ている状況) に限られる。
 */
public class RewriteCapturedSourcesCall {

    /**
     * moduleFragment 全体を走査し、 各 `capturedSources<T>()` 呼び出しのうち T が registered marker
     * のものを `listOf(T(site1), T(site2), ...)` に置換する。
     *
     * 1. [CompatContext.transformCallsInModule] 経由で全 `IrCall` を visit
     * 2. 各 call について [isCapturedSourcesCall] で `me.tbsten.capture.code.capturedSources` 判定
     * 3. type argument T の FqN を抽出し [CaptureCodeMarkerRegistry] に照合 — 未登録かつ T が
     *    `@CaptureCode` meta-annotated なら `CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED` を ERROR
     *    report して skip (bug-001)、 meta annotation 無しなら silent skip (FIR Logic G が error 済)
     * 4. marker FqN ごとに [collectedSites] を filter し、 [BuildMarkerInstance] で `listOf(T(...))`
     *    の [IrExpression] を構築
     * 5. 構築結果を transformer に返して原 call を置換 (null return で no-op)
     *
     * @param moduleFragment IR transform 対象の moduleFragment
     * @param pluginContext IrPluginContext (BuildMarkerInstance が marker class / constructor /
     *   listOf symbol を resolve するために使う)
     * @param compat IR primitive (`transformCallsInModule`, `setCallTypeArgument` 等) を委譲する SPI
     * @param config global Gradle DSL config (per-marker effective config は [CollectedSite] が保持)
     * @param collectedSites Phase 3a の [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite]
     *   の戻り値。 module 全体から収集した site の snapshot
     */
    public operator fun invoke(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        config: CaptureCodePluginConfig,
        collectedSites: List<CollectedSite>,
        messageCollector: MessageCollector = MessageCollector.NONE,
    ) {
        // task-140: caller (= [CaptureCodeIrExtension.generate]) は `collectedSites` を
        // [CollectDeclarationSite] の戻り値として渡す必要がある。 そこで生成される [CollectedSite]
        // は markerFqn を必ず non-blank で詰める (= `markerAnnotations()` filter が
        // `CaptureCodeMarkerRegistry.isMarker(fqn)` を pass した fqn のみ採用する) ため、
        // ここに blank markerFqn の site が混ざるのは caller 側の不変条件破り (= bug)。
        require(collectedSites.all { it.site.markerFqn.isNotBlank() }) {
            "RewriteCapturedSourcesCall: every CollectedSite must carry a non-blank markerFqn. " +
                "Typical root cause: caller passed hand-built CollectedSite instances or mutated " +
                "the CollectDeclarationSite output to drop the marker FqN."
        }

        val buildMarker = BuildMarkerInstance()
        val warnIfNoMarkerFound = WarnIfNoMarkerFound()
        // Tracks marker FQNs we already warned about so the same empty-marker
        // is only reported once per compilation even when multiple
        // `capturedSources<T>()` calls reference it.
        val warnedMarkerFqns = mutableSetOf<String>()
        // task-135: dedupe `CC_CAPTUREDSOURCES_REWRITE_FAILED` /
        // `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warnings per marker FqN. Several
        // `capturedSources<T>()` calls can reference the same broken marker, but
        // the user only needs to hear about each problem once. Pass
        // `MessageCollector.NONE` to BuildMarkerInstance on subsequent calls so
        // only the first invocation actually reports.
        val rewriteFailureWarnedMarkerFqns = mutableSetOf<String>()
        // bug-001: dedupe `CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED` errors per marker FqN.
        // Several `capturedSources<T>()` calls can reference the same unregistered marker,
        // but one ERROR per marker is enough for the user to act on.
        val unregisteredMarkerReportedFqns = mutableSetOf<String>()

        compat.transformCallsInModule(moduleFragment) { call ->
            if (!call.isCapturedSourcesCall()) return@transformCallsInModule null
            val typeArg = compat.getCallTypeArgument(call, 0) ?: return@transformCallsInModule null
            val markerFqn = typeArg.classFqName?.asString() ?: return@transformCallsInModule null
            if (!CaptureCodeMarkerRegistry.isMarker(markerFqn)) {
                // bug-001: T が `@CaptureCode` meta annotation を持つ (= marker のはず) のに
                // registry に居ない = marker declaration が今回の compilation unit に含まれて
                // いない (典型: stale incremental build / 別 module・compilation の marker)。
                // silent skip すると runtime stub が class file に残って実行時に
                // `IllegalStateException` になるため、 compile ERROR に格上げして build を止める。
                // meta annotation を持たない T は FIR phase (Logic G) が既に error 済なので
                // 従来通り silent skip (= 二重 report を避ける)。
                if (typeArg.hasCaptureCodeMetaAnnotation() && unregisteredMarkerReportedFqns.add(markerFqn)) {
                    val text = MessageFormat.format(
                        CapturedSourcesErrors.MARKER_NOT_REGISTERED.message,
                        markerFqn,
                    )
                    messageCollector.report(CompilerMessageSeverity.ERROR, text, null)
                }
                return@transformCallsInModule null
            }
            val sitesForMarker = collectedSites.filter { it.site.markerFqn == markerFqn }
            // task-120-B Phase 7: warn once per marker FqN when opt-in flag is on
            // and the current compilation has zero sites for that marker. The
            // warning is emitted without a file location (the marker FqN in the
            // message body identifies the target uniquely); plumbing IrFile down
            // through `transformCallsInModule` would require additional compat
            // SPI work and isn't justified for an opt-in diagnostic. Future work
            // can attach (file, line, column) via a dedicated SPI when needed.
            if (sitesForMarker.isEmpty() && warnedMarkerFqns.add(markerFqn)) {
                warnIfNoMarkerFound(
                    call = call,
                    markerFqn = markerFqn,
                    siteCount = 0,
                    config = config,
                    file = null,
                    messageCollector = messageCollector,
                )
            }
            val collectorForBuildMarker =
                if (rewriteFailureWarnedMarkerFqns.add(markerFqn)) messageCollector
                else MessageCollector.NONE
            buildMarker(
                call = call,
                markerFqn = markerFqn,
                sites = sitesForMarker,
                pluginContext = pluginContext,
                compat = compat,
                config = config,
                // task-135: forward the IR-phase MessageCollector so the previously
                // silent `?: return null` fall-back paths in BuildMarkerInstance can
                // emit `CC_CAPTUREDSOURCES_REWRITE_FAILED` / `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND`
                // warnings. Existing unit tests that don't go through the registrar
                // pass `MessageCollector.NONE` (= silent, non-breaking). Subsequent
                // calls for the same marker get `NONE` to deduplicate.
                messageCollector = collectorForBuildMarker,
            )
        }
    }

    /**
     * `capturedSources<T>()` 呼び出しの type argument T が registered marker かを判定する helper。
     *
     * Phase 4a 以降 `RewriteCapturedSourcesCall.invoke` の中だけで使うが、 単独テスト可能性のため
     * `internal` で残す (= caller がいなくても unit test だけ可能)。
     */
    internal fun isRegisteredMarker(typeArgumentFqn: String?): Boolean {
        if (typeArgumentFqn == null) return false
        return CaptureCodeMarkerRegistry.isMarker(typeArgumentFqn)
    }

    /**
     * call の callee が `me.tbsten.capture.code.capturedSources` (= 書き換え対象) かを判定する。
     *
     * 判定は **owner function の FqN** で行う ([CaptureCodeCallableIds.capturedSources] の SSoT を
     * 1 度だけ asSingleFqName().asString() で文字列化し、 後段 `IrSimpleFunction.fqNameWhenAvailable`
     * と一致比較)。 これは各 `compat-kXXX/K{XXX}CapturedSourcesTransformer` と同じパターンで、
     * 関数の overload や receiver の有無に関係なく一意に判定できる。
     */
    private fun IrCall.isCapturedSourcesCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == CAPTURED_SOURCES_FQN

    private companion object {
        /**
         * 書き換え対象 `capturedSources<T>()` の完全修飾名。 main module の SSoT
         * [CaptureCodeCallableIds.capturedSources] から派生 (task-091 以降 SSoT 必須)。
         */
        private val CAPTURED_SOURCES_FQN: String =
            CaptureCodeCallableIds.capturedSources.asSingleFqName().asString()
    }
}
