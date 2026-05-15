/*
 * See K220CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k220.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker;
import org.jetbrains.kotlin.fir.declarations.FirRegularClass;

public final class K220MarkerAnnotationCheckerShim extends FirDeclarationChecker<FirRegularClass> {
    public static final K220MarkerAnnotationCheckerShim INSTANCE =
        new K220MarkerAnnotationCheckerShim();

    private K220MarkerAnnotationCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirRegularClass declaration
    ) {
        K220MarkerAnnotationCheckerLogic.INSTANCE.run(context, reporter, declaration);
    }
}
