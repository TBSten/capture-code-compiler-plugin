package me.tbsten.capture.code.warning

/**
 * Small DSL for assembling [CaptureCodeCompilerPluginWarning] values inline at
 * the report site. Symmetric to `errorContext { ... }` in `error/`.
 *
 * task-121: skeleton introduced; concrete usage arrives with the warning
 * implementations in task-123.
 */
public class WarningContextBuilder internal constructor(
    /** Stable warning identifier passed in from the [warningContext] call site. */
    public val id: String,
) {
    /** English-only warning description, possibly with `MessageFormat` placeholders. */
    public var message: String = ""

    /** Optional 1-2 line "Suggested fix" template. */
    public var reply: String? = null

    internal fun build(): CaptureCodeCompilerPluginWarning = object : CaptureCodeCompilerPluginWarning {
        override val id: String = this@WarningContextBuilder.id
        override val message: String = this@WarningContextBuilder.message
        override val reply: String? = this@WarningContextBuilder.reply
    }
}

/**
 * Build a [CaptureCodeCompilerPluginWarning] inline using the
 * [WarningContextBuilder] DSL.
 */
public fun warningContext(
    id: String,
    block: WarningContextBuilder.() -> Unit,
): CaptureCodeCompilerPluginWarning = WarningContextBuilder(id).apply(block).build()
