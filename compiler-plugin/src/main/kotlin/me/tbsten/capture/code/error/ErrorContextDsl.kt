package me.tbsten.capture.code.error

/**
 * Small DSL for assembling [CaptureCodeCompilerPluginError] values inline at
 * the report site. Useful when an error needs to be parameterized by
 * caller-supplied strings without defining a dedicated object per call.
 *
 * Example:
 *
 * ```kotlin
 * val err = errorContext("CC_DEMO") {
 *     message = "Demo error for ''{0}''."
 *     reply = "Remove ''{0}''."
 * }
 * ```
 *
 * task-121: skeleton introduced. Concrete usage will arrive together with the
 * `reportError` helper and the per-feature `*Errors.kt` catalogues.
 */
public class ErrorContextBuilder internal constructor(
    /** Stable error identifier passed in from the [errorContext] call site. */
    public val id: String,
) {
    /** English-only error description, possibly with `MessageFormat` placeholders. */
    public var message: String = ""

    /** Optional 1-2 line "Suggested fix" template. */
    public var reply: String? = null

    internal fun build(): CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = this@ErrorContextBuilder.id
        override val message: String = this@ErrorContextBuilder.message
        override val reply: String? = this@ErrorContextBuilder.reply
    }
}

/**
 * Build a [CaptureCodeCompilerPluginError] inline using the [ErrorContextBuilder]
 * DSL. Intended for ad-hoc errors at the report site; prefer defining
 * `object`-backed instances in a feature-local `*Errors.kt` for any error
 * that fires from more than one location.
 */
public fun errorContext(
    id: String,
    block: ErrorContextBuilder.() -> Unit,
): CaptureCodeCompilerPluginError = ErrorContextBuilder(id).apply(block).build()
