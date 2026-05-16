package me.tbsten.capture.code.error

/**
 * Marker interface for every structured error this compiler plugin can raise.
 *
 * Concrete errors live next to the feature/logic that fires them
 * (see `feature/<feature>/<phase>/<logic>/<Logic>Errors.kt`). Plugin-wide
 * cross-cutting errors are defined in [Errors].
 *
 * ## Fields
 *
 * - [id] — stable, machine-readable error identifier (e.g. `CC_MARKER_IS_EXPECT`).
 *   Used as the key to look up the matching `KtDiagnosticFactory*` in the
 *   active [me.tbsten.capture.code.compat.CompatContext].
 * - [message] — single-sentence English-only error description. The string may
 *   contain `MessageFormat`-style placeholders (e.g. `''{0}''`) when the
 *   underlying factory takes type-arguments.
 * - [reply] — optional 1-2 line "Suggested fix" template. `null` if no
 *   actionable hint is available.
 *
 * task-121: skeleton introduced. Concrete error catalogues are populated by
 * the per-feature `*Errors.kt` files; this interface is the SSoT for the
 * shape they expose.
 */
public interface CaptureCodeCompilerPluginError {
    /** Stable error identifier, e.g. `CC_MARKER_IS_EXPECT`. */
    public val id: String

    /** English-only single-sentence description (no localization). */
    public val message: String

    /** Optional 1-2 line "Suggested fix" template. */
    public val reply: String?
}
