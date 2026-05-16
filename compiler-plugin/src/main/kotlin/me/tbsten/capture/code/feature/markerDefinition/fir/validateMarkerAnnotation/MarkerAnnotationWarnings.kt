package me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings fired alongside Logic F ([ValidateMarkerAnnotation]).
 *
 * Currently holds the single warning produced by the FIR-phase check of
 * `@CaptureCode(...)` argument overrides (Logic F + Logic A side-effect).
 *
 * **English-only** (task-122).
 */
public object MarkerAnnotationWarnings {

    /**
     * `CC_MARKER_OVERRIDE_NO_EFFECT` — `@CaptureCode(option = X)` set one or
     * more options to a value that already matches the active global plugin
     * configuration (i.e. the override has no effect).
     *
     * `{0}` is a comma-separated list of redundant option names
     * (e.g. `includeKdoc, dedent`).
     */
    public val OVERRIDE_NO_EFFECT: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_MARKER_OVERRIDE_NO_EFFECT"
            override val message: String =
                "@CaptureCode override for option(s) ''{0}'' " +
                    "matches the global plugin default and has no effect."
            override val reply: String? =
                "Remove the redundant override(s) to declutter."
        }
}
