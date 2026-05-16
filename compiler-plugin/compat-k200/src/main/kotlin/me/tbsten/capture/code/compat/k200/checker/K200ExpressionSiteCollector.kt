package me.tbsten.capture.code.compat.k200.checker

import me.tbsten.capture.code.compat.k200.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.0.x baseline 向けの **Logic B-fir** checker (entry point)。
 *
 * task-119: ロジック本体は main module の [CollectExpressionSite] に統一された。
 * 本 checker は K2.0 baseline の `check(expression, context, reporter)` signature を
 * override し、 main logic に dispatch する。
 */
internal object K200ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    private val logic = CollectExpressionSite()
    private val compat = CompatContextImpl()

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        logic(context, reporter, expression, compat)
    }
}
