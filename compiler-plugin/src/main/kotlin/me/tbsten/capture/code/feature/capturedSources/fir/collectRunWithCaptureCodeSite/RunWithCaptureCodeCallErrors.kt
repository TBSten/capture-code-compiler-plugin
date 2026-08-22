package me.tbsten.capture.code.feature.capturedSources.fir.collectRunWithCaptureCodeSite

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic B-block
 * ([CollectRunWithCaptureCodeSite]).
 *
 * Unlike Logic F / G, this error is reported through
 * [me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder.reportError]
 * (`CompilerMessageSeverity.ERROR`) instead of a `KtDiagnosticFactory*`:
 * adding a new factory would require touching every
 * `compat-kXXX/CompatContextImpl.kt` nested diagnostics object (factory
 * declaration + renderer MAP entry), and `CompatContext.diagnosticFactory(id)`
 * returns `null` for ids the active compat module does not register. The
 * MessageCollector channel is the same mechanism the IR phase already uses for
 * `CapturedSourceCallErrors`. The `{0}` placeholder is expanded by the caller
 * via `java.text.MessageFormat`, so the doubled-quote escaping (`''`)
 * convention applies.
 *
 * **English-only** (task-122).
 */
public object RunWithCaptureCodeCallErrors {

    /**
     * `CC_RUNWITHCAPTURECODE_MARKER_NOT_CAPTURE_CODE` — the class literal
     * passed as the first argument of `runWithCaptureCode(...)` resolves to a
     * class that is not annotated with `@CaptureCode`. Without the meta
     * annotation the block would silently never be captured, so surface the
     * misuse as a compile error (wording mirrors
     * `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` in
     * [me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.CapturedSourcesCallErrors]).
     * Argument `{0}` is the FQN of the offending class.
     */
    public val MARKER_NOT_CAPTURE_CODE: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_RUNWITHCAPTURECODE_MARKER_NOT_CAPTURE_CODE"
        override val message: String =
            "The marker class passed to runWithCaptureCode(...) must be annotated with @CaptureCode. " +
                "{0} does not have @CaptureCode, so the block would never be captured.\n" +
                "Suggested fix: add ''@CaptureCode'' meta-annotation to {0}, " +
                "or pass a @CaptureCode-meta marker class instead."
        override val reply: String? =
            "Add ''@CaptureCode'' meta-annotation to {0}, " +
                "or pass a @CaptureCode-meta marker class instead."
    }
}
