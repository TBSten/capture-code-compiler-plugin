/*
 * See K240RcCaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k240rc.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall;

public final class K240RcCapturedSourcesCallCheckerShim extends FirExpressionChecker<FirFunctionCall> {
    public static final K240RcCapturedSourcesCallCheckerShim INSTANCE =
        new K240RcCapturedSourcesCallCheckerShim();

    private K240RcCapturedSourcesCallCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirFunctionCall expression
    ) {
        K240RcCapturedSourcesCallCheckerLogic.INSTANCE.run(context, reporter, expression);
    }
}
