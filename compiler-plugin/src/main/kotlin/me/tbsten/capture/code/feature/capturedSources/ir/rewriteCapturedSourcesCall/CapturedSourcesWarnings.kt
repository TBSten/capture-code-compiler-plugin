package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings fired by Logic H ([RewriteCapturedSourcesCall]) when the
 * IR-phase rewrite of `capturedSources<T>()` discovers a pathological state
 * (no markers found, etc.).
 *
 * **English-only** (task-122).
 *
 * task-123: SSoT created. The IR wire-up (calling [NO_MARKER_FOUND] from the
 * actual rewrite pass) is deferred to task-120-B together with the rest of
 * the concrete IR logic.
 */
public object CapturedSourcesWarnings {

    /**
     * `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` — `capturedSources<T>()` was called
     * but no `@T`-annotated declaration or expression was discovered in the
     * current compilation, so the call will return an empty list at runtime
     * (silent fail).
     *
     * Gated behind `CaptureCodePluginConfig.warnOnEmptyCapture = true`
     * (opt-in flag, added together with the IR wire-up in task-120-B) to
     * avoid false positives for multi-module and KMP setups where the
     * markers live in a different compilation than the capture site.
     * See `.local/tmp/task-123-no-marker-found-spike.md`.
     *
     * `{0}` is the marker FQN.
     */
    public val NO_MARKER_FOUND: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_CAPTUREDSOURCES_NO_MARKER_FOUND"
            override val message: String =
                "No ''{0}'' site was found in this compilation. " +
                    "capturedSources<...>() will return an empty list."
            override val reply: String? =
                "Add the marker annotation to at least one declaration or expression, " +
                    "or remove the unused capturedSources<...>() call."
        }
}
