package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfParameterUnused

/**
 * Marker-definition warning (skeleton): would emit
 * `CC_MARKER_PARAMETER_UNUSED` for a marker parameter that carries a default
 * value but is never overridden at any capture site in the compilation.
 *
 * ## Status
 *
 * **Skeleton / not wired-up.** The IR phase has no concrete consumer yet
 * (task-120 partial). Wire-up is deferred to **task-120-B**.
 *
 * ## Wire-up plan (task-120-B)
 *
 * 1. After IR `RewriteCapturedSourcesCall` produces the final marker
 *    instance graph, iterate every marker × parameter pair where the
 *    parameter has a default value.
 * 2. For each pair, check whether any captured site's marker instance carries
 *    a non-default argument for that parameter. If none, call
 *    `WarnIfParameterUnused()(markerFqn, paramName, ...)`.
 * 3. Each `compat-kXXX` supplies a `KtDiagnosticFactoryN` (or a pair of
 *    `KtDiagnosticFactory1<String>` calls — final shape TBD in task-120-B)
 *    for `CC_MARKER_PARAMETER_UNUSED`.
 * 4. The source location should be the parameter declaration on the marker
 *    class.
 */
public class WarnIfParameterUnused {

    /**
     * Placeholder operator. Calling this currently throws because the IR
     * phase has no consumer yet — see the wire-up plan in the class KDoc.
     */
    public operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "WarnIfParameterUnused is a task-123 skeleton. " +
                "Wire-up arrives in task-120-B; see KDoc for the plan.",
        )
}
