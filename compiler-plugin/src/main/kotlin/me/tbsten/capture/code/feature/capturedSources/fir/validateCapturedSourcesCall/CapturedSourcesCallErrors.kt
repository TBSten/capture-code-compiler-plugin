package me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic G ([ValidateCapturedSourcesCall]).
 *
 * The matching `KtDiagnosticFactory*` lives inside each `compat-kXXX`
 * `CompatContextImpl.kt` nested diagnostics object and is looked up by
 * [CaptureCodeCompilerPluginError.id] via `CompatContext.diagnosticFactory(id)`.
 *
 * **English-only** (task-122). Bilingual rendering and locale env var support
 * were retired together with the legacy diagnostic message catalogue.
 */
public object CapturedSourcesCallErrors {

    /**
     * `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — `capturedSources<T>()` was
     * called with `T` not annotated with `@CaptureCode`.
     * Argument `{0}` is the FQN of the offending type.
     */
    public val T_NOT_CAPTURE_CODE: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE"
        // `MessageFormat` 仕様: lone `'` は literal-escape の開始扱いで後続文字列が
        // literal 化される (= placeholder 不展開 + 文字落ち)。 `''` 二重化で literal `'`
        // として render させる。 同種 fix は commit 3aa6a34 (warning 側) 参照。
        override val message: String =
            "Type parameter T of capturedSources<T>() must be annotated with @CaptureCode. " +
                "{0} does not have @CaptureCode.\n" +
                "Suggested fix: add ''@CaptureCode'' meta-annotation to {0}, " +
                "or pass a @CaptureCode-meta marker as T."
        override val reply: String? =
            "Add ''@CaptureCode'' meta-annotation to {0}, " +
                "or pass a @CaptureCode-meta marker as T."
    }
}
