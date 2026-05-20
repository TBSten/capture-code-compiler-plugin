package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings fired by the IR-phase **user-argument re-materialisation**
 * pipeline ([BuildUserArgPrimitive]) when an EXPRESSION-origin marker call (=
 * `@Marker(...)` annotated on an expression statement) carries an enum entry or
 * a `::class` reference that the IR phase cannot rebuild into a proper IR
 * literal.
 *
 * These warnings promote historically-silent `return null` fall-backs (= the
 * argument is dropped and the marker primary constructor's default value
 * silently takes over) to user-visible diagnostics so users no longer have to
 * guess "why is my user arg empty?".
 *
 * **English-only** (task-122). The IR phase emits these via
 * `MessageCollector.report(...)` rather than `DiagnosticReporter` (mirrors
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteFailureWarnings]
 * and `WarnIfNoMarkerFound`; see those KDocs for the K2.0 .. K2.4-RC drift
 * rationale). The matching `KtDiagnosticFactory1<String>` is still registered
 * in each `compat-kXXX/K{XXX}Diagnostics` MAP so the renderer surface stays
 * uniform with the FIR-side warnings.
 *
 * task-134: introduced. Promotes the two silent fall-back paths in
 * [BuildUserArgPrimitive.invoke] for EXPRESSION-origin sites:
 *
 * - [UserArgValue.EnumRef] entry FqN -> no matching `IrEnumEntry` could be
 *   found on the parameter type owner class -> [ENUM_NOT_FOUND]
 * - [UserArgValue.ClassRef] entry FqN -> IR-phase rebuild of an `IrGetClass`
 *   is not implemented yet (0.4.0+ scope) -> [CLASS_REF_UNSUPPORTED]
 *
 * Both warnings are emitted at marker-FqN granularity so a single user typo or
 * unsupported `::class` argument does not flood the build log with one
 * diagnostic per capture site.
 */
public object UserArgWarnings {

    /**
     * `CC_USERARG_ENUM_NOT_FOUND` -- an EXPRESSION-origin marker call carries an
     * enum entry argument (e.g. `verb = Verb.GET`) but the IR phase could not
     * find a matching `IrEnumEntry` on the parameter type's owner class. The
     * argument is silently dropped and the marker primary constructor's default
     * value (if any) takes over.
     *
     * Typical cause: the FIR phase recorded an enum entry FqN whose last
     * segment does not actually exist as an entry on the IR-phase enum class
     * (e.g. an enum entry that was renamed mid-compilation, or a generated
     * stub that never made it to IR phase).
     *
     * `{0}` is the unresolved enum entry FqN (e.g.
     * `com.example.Verb.NOT_EXIST`).
     */
    public val ENUM_NOT_FOUND: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_ENUM_NOT_FOUND"
            override val message: String =
                "Could not resolve enum entry ''{0}'' on the marker user argument. " +
                    "The captured marker instance falls back to the parameter's default value."
            override val reply: String? =
                "Check that the enum entry FQN matches an actual entry on the parameter type, " +
                    "or remove the argument to rely on the marker's default value."
        }

    /**
     * `CC_USERARG_CLASS_REF_UNSUPPORTED` -- an EXPRESSION-origin marker call
     * carries a `::class` reference argument (e.g. `target = MyClass::class`)
     * but the IR phase does not currently re-build the corresponding
     * `IrClassReference` for EXPRESSION-origin sites. The argument is silently
     * dropped and the marker primary constructor's default value (if any) takes
     * over.
     *
     * IR rebuild for `ClassRef` is scoped to 0.4.0+ (see task-134 ticket
     * "Scope-out"). DECLARATION / file-origin sites are unaffected because the
     * IR phase can deep-copy the original `IrClassReference` from the
     * declaration's annotation.
     *
     * `{0}` is the unsupported `::class` target FqN (e.g.
     * `com.example.MyClass`).
     */
    public val CLASS_REF_UNSUPPORTED: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_CLASS_REF_UNSUPPORTED"
            override val message: String =
                "::class reference ''{0}'' on an expression-origin marker user argument " +
                    "is not yet supported; the captured marker instance falls back to " +
                    "the parameter's default value."
            override val reply: String? =
                "Move the `@Marker(...)` annotation to a declaration site (function / class / " +
                    "property / file) so the `::class` reference is preserved verbatim, or " +
                    "remove the argument to rely on the marker's default value."
        }
}
