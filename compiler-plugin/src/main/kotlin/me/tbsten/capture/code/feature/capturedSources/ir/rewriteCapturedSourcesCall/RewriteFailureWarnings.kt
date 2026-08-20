package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings fired by the IR-phase rewrite pipeline
 * ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance])
 * when a previously-silent skip path is hit. These warnings promote the
 * historically silent `return null` fall backs (= `capturedSources<T>()` ends
 * up returning the runtime stub `listOf()`) to user-visible diagnostics so
 * users no longer have to guess "why is my capture empty?".
 *
 * **English-only** (task-122). The IR phase emits these via
 * `MessageCollector.report(...)` rather than `DiagnosticReporter` (mirrors
 * `WarnIfNoMarkerFound` in this same package; see its KDoc for the K2.0 ..
 * K2.4-RC drift rationale). The matching `KtDiagnosticFactory1<String>` is
 * still registered in each `compat-kXXX/K{XXX}Diagnostics` MAP so the renderer
 * surface stays uniform with the FIR-side warnings.
 *
 * task-135: introduced. Promotes the two silent fall-back paths in
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance.invoke]:
 *
 * - marker class FQN that cannot be resolved in the current
 *   `IrPluginContext` (typical cause: cross-module / KMP marker that lives in
 *   a sibling compilation invisible at IR phase) -> [REWRITE_FAILED]
 * - any of the three filler classes (`Source` / `SourceLocation` /
 *   `CaptureKind`) cannot be resolved (typical cause: the `:annotation`
 *   runtime dependency is missing from the consumer's classpath) ->
 *   [FILLER_NOT_FOUND]
 */
public object RewriteFailureWarnings {

    /**
     * `CC_CAPTUREDSOURCES_REWRITE_FAILED` -- a `capturedSources<T>()` /
     * `capturedSource<T>()` call references a marker FQN that was registered by
     * the FIR phase but its `IrClassSymbol` cannot be looked up at IR phase, so
     * the call is left as a runtime stub which will throw
     * `IllegalStateException` at execution time when invoked.
     *
     * Typical cause: the marker class is declared in a different compilation
     * (KMP common module, separate Gradle module, etc.) and the registry
     * snapshot survived across compilations but the IR class symbol does not.
     *
     * `{0}` is the marker FQN.
     */
    public val REWRITE_FAILED: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_CAPTUREDSOURCES_REWRITE_FAILED"
            override val message: String =
                "capturedSources<T>() / capturedSource<T>() rewrite failed for marker ''{0}''. " +
                    "The marker class could not be resolved at IR phase, so the call " +
                    "is left as a runtime stub which will throw IllegalStateException at " +
                    "execution time when invoked."
            override val reply: String? =
                "Ensure the marker class is on the current compilation classpath " +
                    "(check KMP source-set wiring or cross-module dependencies)."
        }

    /**
     * `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` -- the marker resolved fine but one
     * of the three Capture Code filler classes (`Source` / `SourceLocation` /
     * `CaptureKind`) is missing from the consumer's classpath, so the rewrite
     * for this marker is skipped and the call is left as a runtime stub which
     * will throw `IllegalStateException` at execution time when invoked.
     *
     * Typical cause: the consumer applied the compiler plugin but forgot to
     * declare a dependency on the `:annotation` runtime module that ships the
     * filler classes.
     *
     * `{0}` is the marker FQN whose rewrite was skipped.
     */
    public val FILLER_NOT_FOUND: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_CAPTUREDSOURCES_FILLER_NOT_FOUND"
            override val message: String =
                "capturedSources<T>() / capturedSource<T>() rewrite skipped for marker ''{0}'' because " +
                    "the Capture Code runtime filler classes (Source / SourceLocation / " +
                    "CaptureKind) are not on the classpath."
            override val reply: String? =
                "Add the Capture Code annotation runtime dependency " +
                    "(e.g. `implementation(\"me.tbsten.capture.code:annotation:<version>\")`) " +
                    "to the consumer module."
        }
}
