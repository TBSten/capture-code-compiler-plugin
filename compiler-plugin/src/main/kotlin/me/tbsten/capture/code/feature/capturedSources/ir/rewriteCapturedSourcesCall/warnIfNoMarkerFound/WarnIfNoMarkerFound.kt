package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound

/**
 * Logic H (warning side, skeleton): would emit
 * `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` when an IR-phase rewrite of
 * `capturedSources<T>()` finds **no `@T` site** in the current compilation.
 *
 * ## Status
 *
 * **Skeleton / not wired-up.** The IR phase logic that this would hook into
 * (`RewriteCapturedSourcesCall`) is itself still in placeholder form (task-120
 * partial completion). The wire-up is scheduled together with the IR concrete
 * impl in **task-120-B** (`task-120-B-ir-logic-concrete-impl.md`).
 *
 * ## False positive spike
 *
 * See `.local/tmp/task-123-no-marker-found-spike.md`. Multi-module and KMP
 * setups produce false positives because the registry is compilation-scoped.
 * The decided mitigation is to gate the warning behind an **opt-in flag**
 * (`CaptureCodePluginConfig.warnOnEmptyCapture = true`, default `false`),
 * added together with the IR wire-up in task-120-B.
 *
 * ## Wire-up plan (task-120-B)
 *
 * 1. Add `warnOnEmptyCapture: Boolean = false` to `CaptureCodePluginConfig`.
 * 2. In `RewriteCapturedSourcesCall.invoke(...)`, after looking up the marker
 *    site set, call `WarnIfNoMarkerFound()(call, markerFqn, siteCount, ...)`
 *    iff `config.warnOnEmptyCapture && siteCount == 0`.
 * 3. Each `compat-kXXX` supplies a `KtDiagnosticFactory1<String>` for
 *    `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` via the eventual `Diagnostics`
 *    interface (mirroring the FIR-side `WarnIfOverrideNoEffect` pattern).
 */
public class WarnIfNoMarkerFound {

    /**
     * Placeholder operator. Calling this currently throws because the IR
     * phase has no consumer yet — see the wire-up plan in the class KDoc.
     */
    public operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "WarnIfNoMarkerFound is a task-123 skeleton. " +
                "Wire-up arrives in task-120-B; see KDoc for the plan.",
        )
}
