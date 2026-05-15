/*
 * See K220CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k220.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall;

public final class K220CapturedSourcesCallCheckerShim extends FirExpressionChecker<FirFunctionCall> {
    public static final K220CapturedSourcesCallCheckerShim INSTANCE =
        new K220CapturedSourcesCallCheckerShim();

    private K220CapturedSourcesCallCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirFunctionCall expression
    ) {
        K220CapturedSourcesCallCheckerLogic.INSTANCE.run(context, reporter, expression);
    }
}
