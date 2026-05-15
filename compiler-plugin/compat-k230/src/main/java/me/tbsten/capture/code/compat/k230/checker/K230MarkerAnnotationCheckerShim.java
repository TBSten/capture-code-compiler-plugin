/*
 * See K230CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k230.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker;
import org.jetbrains.kotlin.fir.declarations.FirRegularClass;

public final class K230MarkerAnnotationCheckerShim extends FirDeclarationChecker<FirRegularClass> {
    public static final K230MarkerAnnotationCheckerShim INSTANCE =
        new K230MarkerAnnotationCheckerShim();

    private K230MarkerAnnotationCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirRegularClass declaration
    ) {
        K230MarkerAnnotationCheckerLogic.INSTANCE.run(context, reporter, declaration);
    }
}
