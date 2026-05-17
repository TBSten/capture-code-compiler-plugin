package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn

/**
 * Marker-definition warning (skeleton, deferred): would emit
 * `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` when two or more marker classes
 * sharing the same FQN are registered into the IR-phase marker registry.
 *
 * ## Status (task-120-B Phase 7)
 *
 * **Silent no-op.** Wiring this warning requires extending
 * [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 * with a "registration history" mechanism (currently it is a `Set<String>` and
 * duplicates silently dedupe), which falls outside the scope of task-120-B
 * (which focuses on collapsing the runtime drift surface to the main module).
 *
 * Tracked as deferred work in `.local/ticket/task-127-warn-duplicate-marker-fqn-impl.md`.
 *
 * ## When implementation lands
 *
 * 1. At the start of IR phase (after `CollectDeclarationSite` runs), inspect
 *    the merged `CaptureCodeMarkerRegistry`. Group entries by FQN; for any
 *    group with `size > 1`, call [invoke] with `fqn` + reporting context.
 * 2. Each `compat-kXXX` already supplies `K{XXX}Diagnostics.CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`
 *    (a `KtDiagnosticFactory1<String>`), so the diagnostic dispatch surface is
 *    in place. The IR phase reporting layer can route through the same
 *    `MessageCollector` channel used by `WarnIfNoMarkerFound`.
 * 3. The source location should be the first marker declaration's `IrFile`
 *    location (deterministic ordering required).
 *
 * ## Why no-op (not `throw`)
 *
 * Previously this class threw `UnsupportedOperationException` from `invoke()`
 * to flag the unwired state. With the IR chain now fully concrete
 * (task-120-B Phase 5+), any accidental caller would crash the compilation, so
 * `invoke()` is now a no-op to keep the build green while the implementation
 * is deferred to task-127.
 */
public class WarnIfDuplicateMarkerFqn {

    /**
     * Deferred no-op. Returns immediately without reporting anything. The
     * concrete duplicate-FQN detection lives in task-127; see class KDoc.
     */
    public operator fun invoke() {
        // intentionally empty — deferred to task-127
    }
}
