package me.tbsten.capture.code.error

/**
 * SSoT for reusable "Suggested fix" templates referenced from [CaptureCodeCompilerPluginError.reply].
 *
 * Feature-local errors typically set [CaptureCodeCompilerPluginError.reply] to
 * a string literal, but if a reply text is shared between multiple errors,
 * declare it here so the wording stays in sync.
 *
 * task-121: introduced as an empty placeholder. As feature-local `*Errors.kt`
 * files grow, common reply phrases will be lifted here.
 */
public object Replies
