/*
 * See K230CaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k230.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirStatement;

// NOTE: Kotlin 2.2.x で `FirBasicExpressionChecker` は typealias
// (`= FirExpressionChecker<FirStatement>`) になっているため Java から直接 extend 不可。
// generic 化された `FirExpressionChecker<FirStatement>` を継承する。
public final class K230ExpressionSiteCollectorShim extends FirExpressionChecker<FirStatement> {
    public static final K230ExpressionSiteCollectorShim INSTANCE =
        new K230ExpressionSiteCollectorShim();

    private K230ExpressionSiteCollectorShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirStatement expression
    ) {
        K230ExpressionSiteCollectorLogic.INSTANCE.run(context, reporter, expression);
    }
}
