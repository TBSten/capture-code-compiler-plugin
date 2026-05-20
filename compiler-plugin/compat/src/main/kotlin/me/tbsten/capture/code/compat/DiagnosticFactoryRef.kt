package me.tbsten.capture.code.compat

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1

/**
 * Sum type for the values returned by [CompatContext.diagnosticFactory].
 *
 * Previously typed as `Any?`, this sealed class makes call sites (main side
 * `ReportError` / `ReportWarning`) exhaustive over the two factory kinds we
 * actually emit:
 *
 * - [Zero] wraps a no-argument factory ([KtDiagnosticFactory0]).
 * - [OneString] wraps a single-`String` payload factory
 *   ([KtDiagnosticFactory1] of `String`).
 *
 * Each compat-kXXX module re-declares its own `KtDiagnosticFactory*` instances
 * on its native Kotlin baseline (to absorb diagnostic API drift) and wraps
 * them in the matching subclass before publishing through the
 * `K{XXX}Diagnostics.MAP`. Main side callers then dispatch via an exhaustive
 * `when (ref)` block instead of unchecked `as?` casts; a mismatch between the
 * factory kind and the requested overload becomes a fail-fast `error(...)`
 * rather than a silent no-op.
 *
 * `id` is duplicated alongside each variant so that diagnostic messages can
 * reference the originating diagnostic id without re-looking up the MAP.
 */
public sealed class DiagnosticFactoryRef {
    /** The diagnostic id that resolved to this factory (e.g. `CC_MARKER_IS_EXPECT`). */
    public abstract val id: String

    /** Wraps a no-argument [KtDiagnosticFactory0]. */
    public data class Zero(
        override val id: String,
        public val factory: KtDiagnosticFactory0,
    ) : DiagnosticFactoryRef()

    /** Wraps a single-`String` argument [KtDiagnosticFactory1]. */
    public data class OneString(
        override val id: String,
        public val factory: KtDiagnosticFactory1<String>,
    ) : DiagnosticFactoryRef()
}
