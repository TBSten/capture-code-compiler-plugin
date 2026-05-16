package me.tbsten.capture.code.warning

/**
 * Marker interface for every structured warning this compiler plugin can raise.
 *
 * Concrete warnings live next to the feature/logic that fires them
 * (see `feature/<feature>/<phase>/<logic>/<Logic>Warnings.kt`). Plugin-wide
 * cross-cutting warnings are defined in [Warnings].
 *
 * ## Fields
 *
 * - [id] — stable, machine-readable warning identifier
 *   (e.g. `CC_CAPTUREDSOURCES_NO_MARKER_FOUND`).
 * - [message] — single-sentence English-only warning description.
 * - [reply] — optional 1-2 line "Suggested fix" template; `null` if the
 *   warning does not have an obvious actionable fix.
 *
 * task-121: skeleton introduced; concrete warnings will be added in task-123.
 */
public interface CaptureCodeCompilerPluginWarning {
    /** Stable warning identifier. */
    public val id: String

    /** English-only single-sentence description. */
    public val message: String

    /** Optional 1-2 line "Suggested fix" template. */
    public val reply: String?
}
