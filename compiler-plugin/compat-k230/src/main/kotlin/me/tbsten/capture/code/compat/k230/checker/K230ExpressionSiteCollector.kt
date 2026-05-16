// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.compat.k230.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.3.x baseline 向けの **Logic B-fir** entry point (Java shim dispatcher)。
 *
 * task-119: ロジック本体は main module の [CollectExpressionSite] に統一された。
 */
public object K230ExpressionSiteCollectorLogic {
    private val logic = CollectExpressionSite()
    private val compat = CompatContextImpl()

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirStatement,
    ) {
        logic(context, reporter, expression, compat)
    }
}
