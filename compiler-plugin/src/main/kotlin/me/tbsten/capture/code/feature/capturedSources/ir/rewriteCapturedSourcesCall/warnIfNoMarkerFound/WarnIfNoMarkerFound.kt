package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.CapturedSourcesWarnings
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import java.text.MessageFormat

/**
 * Logic H (warning side): emits `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` when an
 * IR-phase rewrite of `capturedSources<T>()` finds **no `@T` site** in the
 * current compilation, but only if the user opted-in via
 * [CaptureCodePluginConfig.warnOnEmptyCapture].
 *
 * ## Status (task-120-B Phase 7)
 *
 * **Wired up**. Driven by [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall]
 * which detects the zero-site condition and calls into [invoke] when the
 * opt-in flag is true. The warning text comes from the SSoT
 * [CapturedSourcesWarnings.NO_MARKER_FOUND] (English-only, task-122 baseline).
 *
 * ## Why MessageCollector instead of DiagnosticReporter
 *
 * The FIR phase has access to `DiagnosticReporter` + `KtDiagnosticFactory*`
 * (resolved through `CompatContext.diagnosticFactory(id)`). IR phase has neither
 * on the K2.0 baseline that main module is compiled against:
 *
 * - `IrPluginContext.getMessageCollector()` only exists from K2.4-RC+
 *   (`createDiagnosticReporter(name)` exists on K2.0 but returns `IrMessageLogger`
 *   which itself was renamed in later versions — drift unsafe to baseline against).
 * - `DiagnosticReporter.reportOn(source, factory, ...)` requires a
 *   `KtSourceElement`, which an IR pass does not naturally produce.
 *
 * The `MessageCollector` interface signature `report(severity, message, location)`
 * is **identical bytecode** on K2.0 .. K2.4-RC (verified via `javap -p` on both
 * baselines), so the main module can call it directly without going through the
 * compat SPI. The collector itself is captured at plugin-registration time by
 * [me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder] (mirrors
 * [me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder]) and threaded
 * to [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall].
 *
 * ## False positive mitigation
 *
 * See `.local/tmp/task-123-no-marker-found-spike.md`. Multi-module and KMP
 * setups can produce false positives because the marker registry is
 * compilation-scoped (B compile-time cannot see A's site set). The opt-in flag
 * defaults to `false` so the default behaviour is conservative.
 */
public class WarnIfNoMarkerFound {

    /**
     * Emit the `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warning when the
     * preconditions are satisfied; otherwise no-op.
     *
     * @param call the `capturedSources<T>()` IR call whose offset is used as the
     *   warning location
     * @param markerFqn fully qualified name of the marker type `T` (used as the
     *   `{0}` placeholder in [CapturedSourcesWarnings.NO_MARKER_FOUND.message])
     * @param siteCount the number of `@T` sites found in the current
     *   compilation (the warning fires iff this is `0`)
     * @param config the active plugin config; only fires when
     *   [CaptureCodePluginConfig.warnOnEmptyCapture] is `true`
     * @param file the IR file containing [call]; used to resolve the source
     *   file path / line / column for the warning location. `null` is
     *   tolerated (the warning still fires but with no location).
     * @param messageCollector the IR-phase [MessageCollector] resolved from
     *   plugin registration. Pass [MessageCollector.NONE] to suppress reporting
     *   entirely (still respects [config] gate).
     */
    public operator fun invoke(
        call: IrCall,
        markerFqn: String,
        siteCount: Int,
        config: CaptureCodePluginConfig,
        file: IrFile?,
        messageCollector: MessageCollector,
    ) {
        if (siteCount > 0) return
        if (!config.warnOnEmptyCapture) return
        val text = MessageFormat.format(
            CapturedSourcesWarnings.NO_MARKER_FOUND.message,
            markerFqn,
        )
        val location = file?.let { irFile ->
            val path = irFile.fileEntry.name
            val line = runCatching { irFile.fileEntry.getLineNumber(call.startOffset) + 1 }.getOrDefault(-1)
            val column = runCatching { irFile.fileEntry.getColumnNumber(call.startOffset) + 1 }.getOrDefault(-1)
            CompilerMessageLocation.create(path, line, column, null)
        }
        messageCollector.report(CompilerMessageSeverity.WARNING, text, location)
    }
}
