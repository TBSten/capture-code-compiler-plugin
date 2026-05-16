package me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic F ([ValidateMarkerAnnotation]).
 *
 * Each entry is the **single source of truth** for the corresponding
 * `CC_MARKER_*` diagnostic. The matching `KtDiagnosticFactory*` instances live
 * inside each `compat-kXXX/CompatContextImpl.kt` nested diagnostics object;
 * those factories are looked up by [CaptureCodeCompilerPluginError.id] via
 * `CompatContext.diagnosticFactory(id)`.
 *
 * **English-only**. Bilingual rendering via the legacy
 * `CaptureCodeDiagnosticMessages` / `CAPTURECODE_LOCALE` is being retired in
 * task-122. Once that retirement completes the renderer chain inside each
 * `compat-kXXX` should reference [message] / [reply] from this catalogue
 * directly.
 *
 * task-121: skeleton introduced. The active `compat-kXXX` diagnostic message
 * strings still come from the legacy `CaptureCodeDiagnosticMessages` until
 * task-122 redirects them here.
 */
public object MarkerAnnotationErrors {

    /**
     * `CC_MARKER_IS_EXPECT` — the `@CaptureCode`-meta marker annotation class
     * is declared as `expect`. Cross-source-set markers are not supported.
     */
    public val IS_EXPECT: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_IS_EXPECT"
        override val message: String =
            "@CaptureCode marker annotation cannot be declared as 'expect'. " +
                "CaptureCode markers must be defined in the same compilation as their use sites."
        override val reply: String? =
            "Remove the 'expect' modifier or move the marker class to commonMain as a concrete annotation."
    }

    /**
     * `CC_MARKER_PARAMETER_TYPE_INVALID` — a parameter on the marker annotation
     * has a type that is outside Kotlin's annotation parameter allow-list.
     * Argument `{0}` is the offending parameter's name.
     */
    public val PARAMETER_TYPE_INVALID: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_PARAMETER_TYPE_INVALID"
        override val message: String =
            "@CaptureCode marker parameter ''{0}'' has an invalid type. " +
                "Allowed types: Source, SourceLocation, CaptureKind, primitives, String, " +
                "KClass, enum, nested annotation, or arrays of these."
        override val reply: String? =
            "Change parameter ''{0}'' to one of the allowed annotation parameter types " +
                "(e.g. String, an enum class, another annotation, or a CaptureCode filler type)."
    }

    /**
     * `CC_MARKER_FILLER_REQUIRES_DEFAULT` — a filler-typed parameter
     * (`Source`, `SourceLocation`, `CaptureKind`) has no default value.
     * Argument `{0}` is the offending parameter's name.
     */
    public val FILLER_REQUIRES_DEFAULT: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_FILLER_REQUIRES_DEFAULT"
        override val message: String =
            "@CaptureCode marker filler parameter ''{0}'' must declare a default value " +
                "(e.g. 'val ${'$'}{0}: Source = Source()'). Filler values are filled in by the compiler."
        override val reply: String? =
            "Assign a default constructor call (e.g. '= Source()') to parameter ''{0}''."
    }
}
