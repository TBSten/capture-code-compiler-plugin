package me.tbsten.capture.code.error

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.DiagnosticFactoryRef
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

/**
 * Report [error] at [source] using the active [compat] implementation to
 * resolve the matching `KtDiagnosticFactory*`.
 *
 * Looks the factory up by [CaptureCodeCompilerPluginError.id] via
 * [CompatContext.diagnosticFactory]. Silently no-ops if the current compat
 * implementation does not register a factory for this id (so a half-migrated
 * compat module does not crash the compilation). A factory-kind mismatch
 * (the id is registered but as `OneString` instead of `Zero`) is treated as
 * a plugin internal bug and surfaced via `error(...)`.
 *
 * Use this when the error site can be expressed without auxiliary type
 * arguments. For factories that take a payload (e.g. parameter name),
 * dispatch to the [reportError] overload that accepts an `arg: String`.
 *
 * task-121: introduced. Existing logic in `feature/.../validate*` still goes
 * through their own `Diagnostics` interface (task-119 pattern); migrating
 * those call sites is intentionally deferred.
 *
 * task-132: switched from `as? KtDiagnosticFactory0` cast to sealed
 * [DiagnosticFactoryRef] dispatch.
 */
public fun DiagnosticReporter.reportError(
    error: CaptureCodeCompilerPluginError,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
) {
    when (val ref = compat.diagnosticFactory(error.id)) {
        null -> return
        is DiagnosticFactoryRef.Zero -> reportOn(source, ref.factory, context)
        is DiagnosticFactoryRef.OneString -> error(
            "diagnostic id '${error.id}' expects a String argument (OneString factory); " +
                "call reportError(..., arg = \"...\") instead of the no-arg overload",
        )
    }
}

/**
 * Report [error] at [source] using a one-argument diagnostic factory resolved
 * from [compat]. The [arg] is forwarded to the factory's message template.
 *
 * Silently no-ops if the active compat implementation has no factory
 * registered for the error id. A factory-kind mismatch (the id is registered
 * but as `Zero` instead of `OneString`) is treated as a plugin internal bug
 * and surfaced via `error(...)`.
 *
 * task-132: switched from `as? KtDiagnosticFactory1<String>` cast to sealed
 * [DiagnosticFactoryRef] dispatch (the previous `@Suppress("UNCHECKED_CAST")`
 * is no longer needed).
 */
public fun DiagnosticReporter.reportError(
    error: CaptureCodeCompilerPluginError,
    source: KtSourceElement?,
    context: CheckerContext,
    compat: CompatContext,
    arg: String,
) {
    when (val ref = compat.diagnosticFactory(error.id)) {
        null -> return
        is DiagnosticFactoryRef.Zero -> error(
            "diagnostic id '${error.id}' is a no-arg factory (Zero), " +
                "but reportError(..., arg = \"$arg\") was called",
        )
        is DiagnosticFactoryRef.OneString -> reportOn(source, ref.factory, arg, context)
    }
}
