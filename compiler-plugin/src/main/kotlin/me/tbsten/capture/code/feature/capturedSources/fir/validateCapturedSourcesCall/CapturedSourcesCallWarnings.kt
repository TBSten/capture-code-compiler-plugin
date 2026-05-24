package me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * Warning ID + message + reply SSoT for Logic G ([ValidateCapturedSourcesCall]).
 *
 * `CapturedSourcesCallErrors` ([T_NOT_CAPTURE_CODE] = ERROR) と対になる warning catalogue。
 * Each `KtDiagnosticFactory1` instance lives inside each `compat-kXXX/CompatContextImpl.kt`
 * nested diagnostics object as `Severity.WARNING`; this catalogue stays English-only
 * (task-122).
 */
public object CapturedSourcesCallWarnings {

    /**
     * `CC_CAPTUREDSOURCES_T_IS_TYPE_PARAMETER` — `capturedSources<T>()` was called
     * inside an `inline fun <reified T : Annotation> ...` (or otherwise with `T`
     * resolved to a type-parameter, not a concrete annotation class).
     *
     * The IR rewriter binds against concrete class symbols at compile time; a
     * type-parameter `T` cannot be rewritten and the call would silently fall back
     * to the runtime `CaptureCode.notApplied()` stub, throwing
     * `IllegalStateException("CaptureCode compiler plugin is not applied")` when
     * the surrounding inline call site is executed. Surfacing the situation at
     * compile time (= "BUG-H provisional warn", task-148) replaces the silent
     * runtime crash with a compile-time signal so the user can either pass a
     * concrete marker class or restructure the helper.
     *
     * Argument `{0}` is the type-parameter name (e.g., `"T"`) for diagnostic
     * clarity.
     */
    public val T_IS_TYPE_PARAMETER: CaptureCodeCompilerPluginWarning = object : CaptureCodeCompilerPluginWarning {
        override val id: String = "CC_CAPTUREDSOURCES_T_IS_TYPE_PARAMETER"
        override val message: String =
            "capturedSources<T>() / capturedSource<T>() cannot be rewritten when T is a type parameter ''{0}'' " +
                "(e.g. inside `inline fun <reified T : Annotation> ...`). " +
                "The call would fall back to the runtime stub and throw at execution time.\n" +
                "Suggested fix: pass a concrete @CaptureCode marker class as T " +
                "(e.g., capturedSources<MyMarker>() or capturedSource<MyMarker>()) at the outermost call site, " +
                "or restructure the helper so the marker class is known at compile time."
        override val reply: String? =
            "Pass a concrete @CaptureCode marker class as T at the outermost call site, " +
                "or restructure the helper so the marker class is known at compile time."
    }
}
