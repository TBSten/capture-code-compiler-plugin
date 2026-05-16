package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn

/**
 * Marker-definition warning (skeleton): would emit
 * `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` when two or more marker classes
 * sharing the same FQN are registered into the IR-phase marker registry.
 *
 * ## Status
 *
 * **Skeleton / not wired-up.** The IR phase has no concrete consumer yet
 * (task-120 partial). Wire-up is deferred to **task-120-B**.
 *
 * ## Wire-up plan (task-120-B)
 *
 * 1. At the start of IR phase (after `CollectDeclarationSite` runs), inspect
 *    the merged `CaptureCodeMarkerRegistry`. Group entries by FQN; for any
 *    group with `size > 1`, call `WarnIfDuplicateMarkerFqn()(fqn, ...)`.
 * 2. Each `compat-kXXX` supplies a `KtDiagnosticFactory1<String>` for
 *    `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`.
 * 3. The source location to report on is the first marker declaration's
 *    `IrFile` location (deterministic ordering required).
 */
public class WarnIfDuplicateMarkerFqn {

    /**
     * Placeholder operator. Calling this currently throws because the IR
     * phase has no consumer yet — see the wire-up plan in the class KDoc.
     */
    public operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "WarnIfDuplicateMarkerFqn is a task-123 skeleton. " +
                "Wire-up arrives in task-120-B; see KDoc for the plan.",
        )
}
