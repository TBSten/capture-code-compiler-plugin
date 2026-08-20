/*
 * See K240CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k240.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall;

public final class K240CapturedSourcesCallCheckerShim extends FirExpressionChecker<FirFunctionCall> {
    public static final K240CapturedSourcesCallCheckerShim INSTANCE =
        new K240CapturedSourcesCallCheckerShim();

    private K240CapturedSourcesCallCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirFunctionCall expression
    ) {
        K240CapturedSourcesCallCheckerLogic.INSTANCE.run(context, reporter, expression);
    }
}
