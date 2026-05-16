package me.tbsten.capture.code.error

import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

/**
 * Report [error] at [source] using the active [compat] implementation to
 * resolve the matching `KtDiagnosticFactory*`.
 *
 * Looks the factory up by [CaptureCodeCompilerPluginError.id] via
 * [CompatContext.diagnosticFactory]. Silently no-ops if the current compat
 * implementation does not register a factory for this id (so a half-migrated
 * compat module does not crash the compilation).
 *
 * Use this when the error site can be expressed without auxiliary type
 * arguments. For factories that take a payload (e.g. parameter name),
 * dispatch to a `KtDiagnosticFactory1<*>`-aware overload at the call site;
 * this helper covers the common `KtDiagnosticFactory0` case.
 *
 * task-121: introduced. Existing logic in `feature/.../validate*` still goes
 * through their own `Diagnostics` interface (task-119 pattern); migrating
 * those call sites is intentionally deferred.
 */
public fun DiagnosticReporter.reportError(
    error: CaptureCodeCompilerPluginError,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
) {
    val factory = compat.diagnosticFactory(error.id) as? KtDiagnosticFactory0 ?: return
    reportOn(source, factory, context)
}

/**
 * Report [error] at [source] using a one-argument diagnostic factory resolved
 * from [compat]. The [arg] is forwarded to the factory's message template.
 *
 * Silently no-ops if the active compat implementation has no
 * `KtDiagnosticFactory1<String>` registered for the error id.
 */
public fun DiagnosticReporter.reportError(
    error: CaptureCodeCompilerPluginError,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
    arg: String,
) {
    @Suppress("UNCHECKED_CAST")
    val factory = compat.diagnosticFactory(error.id) as? KtDiagnosticFactory1<String> ?: return
    reportOn(source, factory, arg, context)
}
