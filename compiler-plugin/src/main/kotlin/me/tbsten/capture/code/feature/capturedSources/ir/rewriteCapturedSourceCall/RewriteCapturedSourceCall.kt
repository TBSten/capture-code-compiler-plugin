package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance
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
 * Logic H' (単数版): `capturedSource<T>()` 呼び出しを **ちょうど 1 件** の `T(...)` に書き換える logic。
 *
 * 複数版 [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall]
 * とは独立の logic として実装する理由:
 *
 * - 単数版固有の件数判定 (0 件 / 複数件 → compile error) は複数版にはない (複数版は 0 件 → 空 list、
 *   複数件 → 全件 list で silent fallback)
 * - rewrite 結果が `IrConstructorCall` (= `T(...)`) で、 複数版の `listOf<T>(T(...), ...)` とは構造が異なる
 * - error 文面 SSoT も独立 ([CapturedSourceCallErrors])
 *
 * [invoke] が module 全体を走査して `capturedSource<T>()` を発見、 件数 0 / >=2 は
 * `MessageCollector.report(ERROR, ...)` 発火 + 原 call を残し、 件数 = 1 は
 * [BuildMarkerInstance.buildOneInstance] で `IrConstructorCall` に置換する。
 *
 * ## ERROR は dedupe する
 *
 * 同一 marker FqN について複数の `capturedSource<T>()` call があり、 すべて 0 件 / 複数件で error
 * 対象の場合、 error は **marker FqN ごとに 1 度だけ** 発火する (= NO_SITE / MULTIPLE_SITES の種別
 * 問わず、 同 marker FqN は最大 1 ERROR)。 これは複数版 `RewriteCapturedSourcesCall` の
 * `warnedMarkerFqns` パターンと対称。 同じ message を call 数分だけ繰り返す build log は noisy
 * なので、 1 度発火すれば user は問題を認識できる。
 *
 * Preconditions: caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate]) は
 * `collectedSites` の各 site が `markerFqn != ""` を満たすことを保証する。 違反時は invoke 冒頭の
 * `require(...)` で fail-fast (複数版と同等)。
 */
public class RewriteCapturedSourceCall {

    /**
     * moduleFragment 全体を走査し、 各 `capturedSource<T>()` 呼び出しのうち T が registered marker
     * のものを件数判定に基づいて transform する。 件数 = 1 は置換、 0 件 / 複数件は ERROR 発火 (
     * marker FqN ごとに 1 回まで dedupe)。
     *
     * @param messageCollector IR-phase MessageCollector。 ERROR 発火に使用
     */
    public operator fun invoke(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        config: CaptureCodePluginConfig,
        collectedSites: List<CollectedSite>,
        messageCollector: MessageCollector = MessageCollector.NONE,
    ) {
        require(collectedSites.all { it.site.markerFqn.isNotBlank() }) {
            "RewriteCapturedSourceCall: every CollectedSite must carry a non-blank markerFqn. " +
                "Typical root cause: caller passed hand-built CollectedSite instances or mutated " +
                "the CollectDeclarationSite output to drop the marker FqN."
        }

        val buildMarker = BuildMarkerInstance()
        // 同一 marker FqN について 0 件 / 複数件 error を call 数分繰り返さないための dedupe set。
        // invariant: 「marker FqN ごとに ERROR 発火は最大 1 回 (NO_SITE / MULTIPLE_SITES の種別問わず)」。
        // 複数版 RewriteCapturedSourcesCall.warnedMarkerFqns と同じパターンを 1 set にまとめた。
        val reportedFqns = mutableSetOf<String>()
        // `BuildMarkerInstance.buildOneInstance` 内部の resolve 失敗 warning
        // (`CC_CAPTUREDSOURCES_REWRITE_FAILED` / `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND`) を
        // marker FqN ごとに 1 度だけに dedupe するための set。 複数版
        // `RewriteCapturedSourcesCall.rewriteFailureWarnedMarkerFqns` と同じパターン。
        // 同一 marker を複数の `capturedSource<T>()` call で参照する場合、 build log の
        // noisy duplicate を防ぐため 2 回目以降は `MessageCollector.NONE` を渡す。
        val rewriteFailureWarnedMarkerFqns = mutableSetOf<String>()

        compat.transformCallsInModule(moduleFragment) { call ->
            if (!call.isCapturedSourceCall()) return@transformCallsInModule null
            val markerFqn = call.markerFqnOf(compat) ?: return@transformCallsInModule null
            val sitesForMarker = collectedSites.filter { it.site.markerFqn == markerFqn }

            when (sitesForMarker.size) {
                0 -> {
                    if (reportedFqns.add(markerFqn)) {
                        val text = MessageFormat.format(
                            CapturedSourceCallErrors.NO_SITE.message,
                            markerFqn,
                        )
                        messageCollector.report(CompilerMessageSeverity.ERROR, text, null)
                    }
                    null
                }
                1 -> {
                    val collectorForBuildMarker =
                        if (rewriteFailureWarnedMarkerFqns.add(markerFqn)) messageCollector
                        else MessageCollector.NONE
                    buildMarker.buildOneInstance(
                        markerFqn = markerFqn,
                        site = sitesForMarker.single(),
                        pluginContext = pluginContext,
                        compat = compat,
                        config = config,
                        messageCollector = collectorForBuildMarker,
                    ) as IrExpression?
                }
                else -> {
                    if (reportedFqns.add(markerFqn)) {
                        val locations = sitesForMarker.joinToString(", ") { collected ->
                            val fp = collected.site.filePath
                            val ln = collected.site.startLine
                            when {
                                fp.isBlank() && ln == 0 -> "<unknown location>"
                                fp.isBlank() -> "<unknown>:$ln"
                                ln == 0 -> "$fp:<unknown line>"
                                else -> "$fp:$ln"
                            }
                        }
                        val text = MessageFormat.format(
                            CapturedSourceCallErrors.MULTIPLE_SITES.message,
                            markerFqn,
                            locations,
                        )
                        messageCollector.report(CompilerMessageSeverity.ERROR, text, null)
                    }
                    null
                }
            }
        }
    }

    /**
     * call の callee が `me.tbsten.capture.code.capturedSource` (= 単数版書き換え対象) かを判定する。
     * 複数版 `capturedSources` は別 CallableId (`CAPTURED_SOURCES_FQN`) なので干渉しない。
     */
    private fun IrCall.isCapturedSourceCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == CAPTURED_SOURCE_FQN

    /**
     * type argument 0 (= `capturedSource<T>()` の T) を取り出し、 [CaptureCodeMarkerRegistry] に
     * 登録された marker FqN ならそれを返し、 そうでなければ null。
     */
    private fun IrCall.markerFqnOf(compat: CompatContext): String? {
        val typeArg = compat.getCallTypeArgument(this, 0) ?: return null
        val fqn = typeArg.classFqName?.asString() ?: return null
        return fqn.takeIf { CaptureCodeMarkerRegistry.isMarker(it) }
    }

    private companion object {
        // 書き換え対象 `capturedSource<T>()` の完全修飾名 (SSoT: [CaptureCodeCallableIds.capturedSource])。
        private val CAPTURED_SOURCE_FQN: String =
            CaptureCodeCallableIds.capturedSource.asSingleFqName().asString()
    }
}
