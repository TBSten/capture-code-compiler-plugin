package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.warning.CaptureCodeCompilerPluginWarning

/**
 * SSoT for warnings fired by the IR-phase **user-argument re-materialisation**
 * pipeline ([BuildUserArgPrimitive]) when an EXPRESSION-origin marker call (=
 * `@Marker(...)` annotated on an expression statement) carries an argument that
 * the IR phase cannot rebuild into a proper IR value.
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
 * rationale).
 *
 * task-134: introduced with `CC_USERARG_ENUM_NOT_FOUND` /
 * `CC_USERARG_CLASS_REF_UNSUPPORTED`.
 *
 * bug-004: `CC_USERARG_CLASS_REF_UNSUPPORTED` is no longer fired because
 * ClassRef re-materialisation is now implemented (`IrClassReferenceShim`);
 * resolve failures fall to [CLASS_NOT_FOUND] instead. The member itself is
 * kept because every `compat-kXXX/K{XXX}Diagnostics` renderer map references
 * `UserArgWarnings.CLASS_REF_UNSUPPORTED.message` at compile time.
 * [EXPRESSION_UNSUPPORTED] was added for FIR-side conversion failures
 * (compound constant expressions, non-const references, ...) which previously
 * surfaced as a misleading "Could not resolve enum entry
 * 'kotlin.Long.unaryMinus'" message — or, for array literals, as no message
 * at all.
 */
public object UserArgWarnings {

    /**
     * `CC_USERARG_ENUM_NOT_FOUND` -- an EXPRESSION-origin marker call carries an
     * enum entry argument (e.g. `verb = Verb.GET`) but the IR phase could not
     * find a matching `IrEnumEntry` on the parameter type's owner class. The
     * argument is dropped and the marker primary constructor's default value
     * (if any) takes over.
     *
     * bug-004: the FIR phase now records [UserArgValue.EnumRef] only for
     * references that actually resolve to a `FirEnumEntrySymbol`, so this
     * warning no longer mislabels const references / operator calls as
     * "enum entry". The remaining trigger is an entry FqN whose last segment
     * does not exist on the IR-phase enum class (e.g. an entry renamed
     * mid-compilation).
     *
     * `{0}` is the unresolved enum entry FqN (e.g.
     * `com.example.Verb.NOT_EXIST`).
     */
    public val ENUM_NOT_FOUND: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_ENUM_NOT_FOUND"
            // task-charter-6 (apostrophe bug fix): MessageFormat eats lone apostrophes
            // unless they are escaped as ''. Without the escape, "parameter's" is
            // rendered as "parameters" and any subsequent `{0}` would also be lost.
            override val message: String =
                "Could not resolve enum entry ''{0}'' on the marker user argument. " +
                    "The captured marker instance falls back to the parameter''s default value."
            override val reply: String? =
                "Check that the enum entry FQN matches an actual entry on the parameter type, " +
                    "or remove the argument to rely on the marker's default value."
        }

    /**
     * `CC_USERARG_CLASS_NOT_FOUND` -- an EXPRESSION-origin marker call carries a
     * `::class` reference or a nested annotation argument whose class FqN could
     * not be resolved to an `IrClass` on the IR-phase classpath. The argument is
     * dropped and the marker primary constructor's default value (if any) takes
     * over.
     *
     * bug-004: replaces the retired `CC_USERARG_CLASS_REF_UNSUPPORTED` --
     * `::class` / nested annotation re-materialisation is implemented now, so
     * the only remaining fall-back cause is symbol resolution failure (e.g. the
     * referenced class is not on the compilation classpath at IR phase).
     *
     * `{0}` is the unresolvable class FqN (e.g. `com.example.MySvc`).
     */
    public val CLASS_NOT_FOUND: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_CLASS_NOT_FOUND"
            // task-charter-6 (apostrophe bug fix): see ENUM_NOT_FOUND above.
            override val message: String =
                "Could not resolve class ''{0}'' referenced by the marker user argument. " +
                    "The captured marker instance falls back to the parameter''s default value."
            override val reply: String? =
                "Check that the referenced class is on the compilation classpath, " +
                    "or remove the argument to rely on the marker's default value."
        }

    /**
     * `CC_USERARG_CLASS_REF_UNSUPPORTED` -- retired since bug-004 (ClassRef
     * re-materialisation via `IrClassReferenceShim` made it unreachable), but
     * **kept as a member** because each `compat-kXXX/K{XXX}Diagnostics`
     * renderer map references `UserArgWarnings.CLASS_REF_UNSUPPORTED.message`
     * at compile time (the compat modules are versioned independently and are
     * not touched by bug-004). Do not fire this from new code; use
     * [CLASS_NOT_FOUND] / [EXPRESSION_UNSUPPORTED] instead.
     *
     * `{0}` is the unsupported `::class` target FqN (e.g.
     * `com.example.MyClass`).
     */
    public val CLASS_REF_UNSUPPORTED: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_CLASS_REF_UNSUPPORTED"
            // task-charter-6 (apostrophe bug fix): see ENUM_NOT_FOUND above.
            override val message: String =
                "::class reference ''{0}'' on an expression-origin marker user argument " +
                    "is not yet supported; the captured marker instance falls back to " +
                    "the parameter''s default value."
            override val reply: String? =
                "Move the `@Marker(...)` annotation to a declaration site (function / class / " +
                    "property / file) so the `::class` reference is preserved verbatim, or " +
                    "remove the argument to rely on the marker's default value."
        }

    /**
     * `CC_USERARG_EXPRESSION_UNSUPPORTED` -- an EXPRESSION-origin marker call
     * carries an argument expression that the FIR phase could not convert into
     * a re-materialisable value: a compound constant expression
     * (`BASE * 2 + 1`), a non-const reference, an `arrayOf(...)` call, and so
     * on. The argument is dropped and the marker primary constructor's default
     * value (if any) takes over.
     *
     * Simple negative literals (`-42L`), `const val` references and array
     * literals (`["a", "b"]`) ARE folded / re-materialised since bug-004 and do
     * not trigger this warning. DECLARATION / file-origin sites are never
     * affected because the IR phase deep-copies the original argument
     * expression verbatim.
     *
     * `{0}` is a short source snippet of the unsupported argument expression
     * (e.g. `BASE * 2 + 1`).
     */
    public val EXPRESSION_UNSUPPORTED: CaptureCodeCompilerPluginWarning =
        object : CaptureCodeCompilerPluginWarning {
            override val id: String = "CC_USERARG_EXPRESSION_UNSUPPORTED"
            // task-charter-6 (apostrophe bug fix): see ENUM_NOT_FOUND above.
            override val message: String =
                "Marker user argument ''{0}'' is a constant expression that is not supported " +
                    "on expression-origin markers; the captured marker instance falls back to " +
                    "the parameter''s default value."
            override val reply: String? =
                "Pre-compute the value into a plain literal (or move the `@Marker(...)` " +
                    "annotation to a declaration site, where any constant expression is " +
                    "preserved verbatim)."
        }
}
