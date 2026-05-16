// Kotlin 2.4.0-RC: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc.checker

import me.tbsten.capture.code.compat.k240rc.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.4.0-RC baseline 向けの **Logic B-fir** entry point (Java shim dispatcher)。
 *
 * task-119: ロジック本体は main module の [CollectExpressionSite] に統一された。
 */
public object K240RcExpressionSiteCollectorLogic {
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
