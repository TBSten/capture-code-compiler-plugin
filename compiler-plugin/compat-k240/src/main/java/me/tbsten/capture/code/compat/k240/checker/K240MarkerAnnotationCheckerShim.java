/*
 * See K240CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k240.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker;
import org.jetbrains.kotlin.fir.declarations.FirRegularClass;

public final class K240MarkerAnnotationCheckerShim extends FirDeclarationChecker<FirRegularClass> {
    public static final K240MarkerAnnotationCheckerShim INSTANCE =
        new K240MarkerAnnotationCheckerShim();

    private K240MarkerAnnotationCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirRegularClass declaration
    ) {
        K240MarkerAnnotationCheckerLogic.INSTANCE.run(context, reporter, declaration);
    }
}
