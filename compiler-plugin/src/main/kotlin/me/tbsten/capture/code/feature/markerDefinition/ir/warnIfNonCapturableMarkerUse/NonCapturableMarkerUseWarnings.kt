package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfNonCapturableMarkerUse

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * Warning message SSoT for [WarnIfNonCapturableMarkerUse].
 *
 * Reported through the IR-phase `MessageCollector` (same channel as
 * `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` /
 * `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`), not through a
 * `KtDiagnosticFactory*` — adding a factory would require touching every
 * `compat-kXXX/CompatContextImpl.kt` diagnostics object. Placeholders are
 * expanded by the caller via `java.text.MessageFormat`, so the doubled-quote
 * escaping (`''`) convention applies.
 *
 * **English-only** (task-122).
 */
public object NonCapturableMarkerUseWarnings {

    /**
     * `CC_MARKER_ON_NON_CAPTURABLE_TARGET` — a registered `@CaptureCode` marker
     * annotation is attached to a position the plugin never captures, so the
     * annotation is a silent no-op. Detected positions:
     *
     * - property accessors, i.e. use-site targets such as `@get:Marker` /
     *   `@set:Marker` (the annotation moves onto the accessor function in IR
     *   and the declaration collector deliberately skips accessors)
     * - enum entries (`@Marker RED,`) — enum entries are not part of the
     *   declaration walk
     *
     * `{0}` is the marker FQN, `{1}` is a short description of the position.
     */
    public val MARKER_ON_NON_CAPTURABLE_TARGET: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_MARKER_ON_NON_CAPTURABLE_TARGET"
            override val message: String =
                "@CaptureCode marker ''{0}'' is attached to {1}, which the CaptureCode plugin " +
                    "does not capture. The annotation has no effect here and " +
                    "capturedSources<...>() will not include this site.\n" +
                    "Suggested fix: attach the marker directly to a class / function / property / " +
                    "typealias declaration (without a use-site target), or capture an expression " +
                    "with runWithCaptureCode(...)."
            override val reply: String? =
                "Attach the marker directly to a class / function / property / typealias " +
                    "declaration (without a use-site target), or capture an expression with " +
                    "runWithCaptureCode(...)."
        }
}
