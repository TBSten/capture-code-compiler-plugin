/*
 * See K230CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k230.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall;

public final class K230CapturedSourcesCallCheckerShim extends FirExpressionChecker<FirFunctionCall> {
    public static final K230CapturedSourcesCallCheckerShim INSTANCE =
        new K230CapturedSourcesCallCheckerShim();

    private K230CapturedSourcesCallCheckerShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirFunctionCall expression
    ) {
        K230CapturedSourcesCallCheckerLogic.INSTANCE.run(context, reporter, expression);
    }
}
