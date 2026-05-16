package me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic G ([ValidateCapturedSourcesCall]).
 *
 * The matching `KtDiagnosticFactory*` lives inside each `compat-kXXX`
 * `CompatContextImpl.kt` nested diagnostics object and is looked up by
 * [CaptureCodeCompilerPluginError.id] via `CompatContext.diagnosticFactory(id)`.
 *
 * **English-only**. Bilingual rendering via the legacy
 * `CaptureCodeDiagnosticMessages` will be retired in task-122.
 */
public object CapturedSourcesCallErrors {

    /**
     * `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — `capturedSources<T>()` was
     * called with `T` not annotated with `@CaptureCode`.
     * Argument `{0}` is the FQN of the offending type.
     */
    public val T_NOT_CAPTURE_CODE: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE"
        override val message: String =
            "capturedSources<T>() requires T to be an annotation class marked with @CaptureCode. " +
                "Found: {0}."
        override val reply: String? =
            "Add the @CaptureCode meta-annotation to {0}, or pass a marker annotation already " +
                "annotated with @CaptureCode as T."
    }
}
