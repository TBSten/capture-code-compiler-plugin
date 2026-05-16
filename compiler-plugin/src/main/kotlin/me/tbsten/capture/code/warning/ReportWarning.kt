package me.tbsten.capture.code.warning

import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

/**
 * Report [warning] at [source] using the active [compat] implementation to
 * resolve the matching `KtDiagnosticFactory*`.
 *
 * Silently no-ops if the active compat implementation has no factory
 * registered for the warning id (so a half-migrated compat module does not
 * crash the compilation).
 *
 * task-121: introduced. Concrete warning factories arrive in task-123.
 */
public fun DiagnosticReporter.reportWarning(
    warning: CaptureCodeCompilerPluginWarning,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
) {
    val factory = compat.diagnosticFactory(warning.id) as? KtDiagnosticFactory0 ?: return
    reportOn(source, factory, context)
}

/**
 * Report [warning] at [source] using a one-argument warning factory resolved
 * from [compat]. The [arg] is forwarded to the factory's message template.
 */
public fun DiagnosticReporter.reportWarning(
    warning: CaptureCodeCompilerPluginWarning,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
    arg: String,
) {
    @Suppress("UNCHECKED_CAST")
    val factory = compat.diagnosticFactory(warning.id) as? KtDiagnosticFactory1<String> ?: return
    reportOn(source, factory, arg, context)
}
