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
 * **English-only** (task-122). Bilingual rendering and locale env var support
 * were retired together with the legacy diagnostic message catalogue.
 * Each `compat-kXXX` renderer reads [message] from this catalogue directly.
 *
 * ## Wording policy
 *
 * Messages keep the back-compat phrases that existing checker tests already
 * assert against (`shouldContain "has an unsupported type"` etc.), so that the
 * task-121 SSoT lift does not regress test coverage. The `Suggested fix:`
 * suffix and the parameter-name placeholder are baked into [message] because
 * Kotlin's `KtDiagnosticFactoryToRendererMap.put(...)` takes a single message
 * string. The standalone [reply] template is kept for parity with the
 * interface contract and for tooling that wants to surface the hint
 * separately.
 *
 * `MessageFormat` placeholders use the doubled-quote form (`''{0}''`) so the
 * single quotes render literally after `MessageFormat` parsing.
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
                "Markers must be concrete annotation declarations (see design §7.6).\n" +
                "Suggested fix: remove the 'expect' modifier; declare the marker concretely in commonMain."
        override val reply: String? =
            "Remove the 'expect' modifier; declare the marker concretely in commonMain."
    }

    /**
     * `CC_MARKER_PARAMETER_TYPE_INVALID` — a parameter on the marker annotation
     * has a type that is outside Kotlin's annotation parameter allow-list.
     * Argument `{0}` is the offending parameter's name.
     */
    public val PARAMETER_TYPE_INVALID: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_PARAMETER_TYPE_INVALID"
        override val message: String =
            "@CaptureCode marker annotation parameter ''{0}'' has an unsupported type. " +
                "Kotlin annotation parameter types are limited to primitives, String, KClass, " +
                "enum, annotation, or arrays of these.\n" +
                "Suggested fix: change parameter ''{0}'' to one of the allowed annotation types " +
                "(e.g., String, Int, an enum class, or another annotation)."
        override val reply: String? =
            "Change parameter ''{0}'' to one of the allowed annotation types " +
                "(e.g., String, Int, an enum class, or another annotation)."
    }

    /**
     * `CC_MARKER_FILLER_REQUIRES_DEFAULT` — a filler-typed parameter
     * (`Source`, `SourceLocation`, `CaptureKind`) has no default value.
     * Argument `{0}` is the offending parameter's name.
     */
    public val FILLER_REQUIRES_DEFAULT: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_FILLER_REQUIRES_DEFAULT"
        override val message: String =
            "@CaptureCode marker filler parameter ''{0}'' must have a default value " +
                "(e.g., 'val source: Source = Source()'). The plugin auto-fills filler values " +
                "at compile time, so use sites do not specify them explicitly.\n" +
                "Suggested fix: assign a default constructor call (e.g., '= Source()') to ''{0}''."
        override val reply: String? =
            "Assign a default constructor call (e.g., '= Source()') to parameter ''{0}''."
    }
}
