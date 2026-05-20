package me.tbsten.capture.code.warning

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.DiagnosticFactoryRef
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

/**
 * Report [warning] at [source] using the active [compat] implementation to
 * resolve the matching `KtDiagnosticFactory*`.
 *
 * Silently no-ops if the active compat implementation has no factory
 * registered for the warning id (so a half-migrated compat module does not
 * crash the compilation). A factory-kind mismatch (the id is registered but
 * as `OneString` instead of `Zero`) is treated as a plugin internal bug and
 * surfaced via `error(...)`.
 *
 * task-121: introduced. Concrete warning factories arrive in task-123.
 *
 * task-132: switched from `as? KtDiagnosticFactory0` cast to sealed
 * [DiagnosticFactoryRef] dispatch.
 */
public fun DiagnosticReporter.reportWarning(
    warning: CaptureCodeCompilerPluginWarning,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
) {
    when (val ref = compat.diagnosticFactory(warning.id)) {
        null -> return
        is DiagnosticFactoryRef.Zero -> reportOn(source, ref.factory, context)
        is DiagnosticFactoryRef.OneString -> error(
            "diagnostic id '${warning.id}' expects a String argument (OneString factory); " +
                "call reportWarning(..., arg = \"...\") instead of the no-arg overload",
        )
    }
}

/**
 * Report [warning] at [source] using a one-argument warning factory resolved
 * from [compat]. The [arg] is forwarded to the factory's message template.
 *
 * Silently no-ops if the active compat implementation has no factory
 * registered for the warning id. A factory-kind mismatch (the id is
 * registered but as `Zero` instead of `OneString`) is treated as a plugin
 * internal bug and surfaced via `error(...)`.
 *
 * task-132: switched from `as? KtDiagnosticFactory1<String>` cast to sealed
 * [DiagnosticFactoryRef] dispatch (the previous `@Suppress("UNCHECKED_CAST")`
 * is no longer needed).
 */
public fun DiagnosticReporter.reportWarning(
    warning: CaptureCodeCompilerPluginWarning,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
    arg: String,
) {
    when (val ref = compat.diagnosticFactory(warning.id)) {
        null -> return
        is DiagnosticFactoryRef.Zero -> error(
            "diagnostic id '${warning.id}' is a no-arg factory (Zero), " +
                "but reportWarning(..., arg = \"$arg\") was called",
        )
        is DiagnosticFactoryRef.OneString -> reportOn(source, ref.factory, arg, context)
    }
}
