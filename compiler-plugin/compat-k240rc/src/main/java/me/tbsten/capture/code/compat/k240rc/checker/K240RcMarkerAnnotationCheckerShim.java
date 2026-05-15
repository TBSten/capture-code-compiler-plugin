/*
 * See K240RcCaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k240rc.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker;
import org.jetbrains.kotlin.fir.declarations.FirRegularClass;

public final class K240RcMarkerAnnotationCheckerShim extends FirDeclarationChecker<FirRegularClass> {
    public static final K240RcMarkerAnnotationCheckerShim INSTANCE =
        new K240RcMarkerAnnotationCheckerShim();

    private K240RcMarkerAnnotationCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirRegularClass declaration
    ) {
        K240RcMarkerAnnotationCheckerLogic.INSTANCE.run(context, reporter, declaration);
    }
}
