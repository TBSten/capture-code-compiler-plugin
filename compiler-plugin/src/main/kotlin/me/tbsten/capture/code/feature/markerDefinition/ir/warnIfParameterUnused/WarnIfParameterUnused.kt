package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfParameterUnused

/**
 * Marker-definition warning (skeleton, deferred): would emit
 * `CC_MARKER_PARAMETER_UNUSED` for a marker parameter that carries a default
 * value but is never overridden at any capture site in the compilation.
 *
 * ## Status (task-120-B Phase 7)
 *
 * **Silent no-op.** Implementing this warning correctly requires the IR phase
 * to (a) resolve each marker class' constructor symbol via `IrPluginContext`,
 * (b) iterate `valueParameters` for default values, (c) walk every captured
 * site's marker call expression to check whether each parameter is overridden,
 * and (d) deduplicate per `(markerFqn, paramName)` pair. This logic is
 * medium-cost and orthogonal to the runtime-drift collapse that drives
 * task-120-B, so it is deferred.
 *
 * Tracked as deferred work in `.local/ticket/task-128-warn-parameter-unused-impl.md`.
 *
 * ## When implementation lands
 *
 * 1. After IR `RewriteCapturedSourcesCall` produces the final marker
 *    instance graph, iterate every (marker, parameter) pair where the
 *    parameter has a default value.
 * 2. For each pair, check whether any captured site's marker instance carries
 *    a non-default argument for that parameter. If none, call
 *    `WarnIfParameterUnused()(markerFqn, paramName, ...)`.
 * 3. Each `compat-kXXX` already supplies `K{XXX}Diagnostics.CC_MARKER_PARAMETER_UNUSED`
 *    (`KtDiagnosticFactory1<String>`); the MessageCollector channel used by
 *    `WarnIfNoMarkerFound` works for the IR-phase reporting.
 * 4. The source location should be the parameter declaration on the marker
 *    class.
 *
 * ## Why no-op (not `throw`)
 *
 * Symmetry with `WarnIfDuplicateMarkerFqn`: the IR chain is now fully concrete
 * (task-120-B Phase 5+), so an accidental caller of this class must not crash
 * the compilation. `invoke()` is therefore a no-op until task-128 lands.
 */
public class WarnIfParameterUnused {

    /**
     * Deferred no-op. Returns immediately without reporting anything. The
     * concrete unused-parameter detection lives in task-128; see class KDoc.
     */
    public operator fun invoke() {
        // intentionally empty — deferred to task-128
    }
}
