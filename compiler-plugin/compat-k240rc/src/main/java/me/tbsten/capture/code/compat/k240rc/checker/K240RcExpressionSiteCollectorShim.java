/*
 * See K240RcCaptureCodeMarkerClassCheckerShim.java for the rationale.
 */
package me.tbsten.capture.code.compat.k240rc.checker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter;
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind;
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext;
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker;
import org.jetbrains.kotlin.fir.expressions.FirStatement;

// NOTE: Kotlin 2.2.x で `FirBasicExpressionChecker` は typealias
// (`= FirExpressionChecker<FirStatement>`) になっているため Java から直接 extend 不可。
// generic 化された `FirExpressionChecker<FirStatement>` を継承する。
public final class K240RcExpressionSiteCollectorShim extends FirExpressionChecker<FirStatement> {
    public static final K240RcExpressionSiteCollectorShim INSTANCE =
        new K240RcExpressionSiteCollectorShim();

    private K240RcExpressionSiteCollectorShim() {
        super(MppCheckerKind.Common);
    }

    @Override
    public void check(
        @NotNull CheckerContext context,
        @NotNull DiagnosticReporter reporter,
        @NotNull FirStatement expression
    ) {
        K240RcExpressionSiteCollectorLogic.INSTANCE.run(context, reporter, expression);
    }
}
