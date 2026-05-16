package me.tbsten.capture.code.feature.markerDefinition

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings that fire during **marker definition** discovery / validation
 * across the whole module (not bound to a specific FIR / IR sub-logic).
 *
 * Each entry returns a fresh [CaptureCodeCompilerPluginWarning] so dynamic
 * arguments (FQN, parameter name, ...) can be baked into the message body. The
 * matching `KtDiagnosticFactory*` is supplied by each
 * `compat-kXXX/CompatContextImpl.kt` nested `K{XXX}Diagnostics` and is resolved
 * by [CaptureCodeCompilerPluginWarning.id] via `CompatContext.diagnosticFactory(id)`.
 *
 * **English-only** (task-122).
 *
 * task-123: skeleton SSoT created. The IR phase wire-up that actually fires
 * these warnings is deferred to **task-120-B** (`task-120-B-ir-logic-concrete-impl.md`).
 * The factories live in each `compat-kXXX/K{XXX}Diagnostics` already, so the
 * `compat`-side surface is in place.
 */
public object MarkerDefinitionWarnings {

    /**
     * `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` — multiple marker classes share
     * the same fully-qualified name (FQN), e.g. one declared in `commonMain`
     * and another shadow declaration in a platform-specific source set. The
     * IR-phase registry sees both and the captured-sources lookup becomes
     * ambiguous.
     *
     * `{0}` is the offending marker FQN.
     */
    public val DUPLICATE_MARKER_FQN: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN"
            override val message: String =
                "Multiple `@CaptureCode` markers share the FQN ''{0}''. " +
                    "capturedSources<...>() lookups for this marker become ambiguous and " +
                    "may capture an unintended site set."
            override val reply: String? =
                "Rename one of the duplicate markers, or move the declaration so only one " +
                    "exists per compilation."
        }

    /**
     * `CC_MARKER_PARAMETER_UNUSED` — a parameter on a `@CaptureCode` marker has
     * a default value but is **never** overridden by any of the call sites
     * captured for that marker. The parameter is dead weight.
     *
     * `{0}` is the dotted `"<markerFqn>.<paramName>"` payload built by the IR
     * logic (`WarnIfParameterUnused`). Concatenating before reporting lets us
     * keep the renderer compatible with `KtDiagnosticFactory1<String>` on
     * every Kotlin baseline (no `KtDiagnosticFactory2` drift to worry about).
     */
    public val PARAMETER_UNUSED: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_MARKER_PARAMETER_UNUSED"
            override val message: String =
                "Marker parameter ''{0}'' has a default value but is never overridden " +
                    "at any capture site. The parameter is unused."
            override val reply: String? =
                "Remove the parameter from the marker, or override it at least once at a capture site."
        }
}
