package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.compat.k202.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.0.21 baseline 向けの **Logic B-fir** checker (entry point)。
 *
 * task-119: ロジック本体は main module の [CollectExpressionSite] に統一された。
 */
internal object K202ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    private val logic = CollectExpressionSite()
    private val compat = CompatContextImpl()

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        logic(context, reporter, expression, compat)
    }
}
