package me.tbsten.capture.code.compat.k200.checker

import me.tbsten.capture.code.compat.k200.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.0.x baseline 向けの **Logic G** checker (entry point)。
 *
 * task-119: ロジック本体は main module の [ValidateCapturedSourcesCall] に統一された。
 * 本 checker は K2.0 baseline の `check(expression, context, reporter)` signature を
 * override し、 [K200CaptureCodeDiagnostics] の factory を渡して main logic に dispatch する。
 */
internal object K200CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    private val logic = ValidateCapturedSourcesCall()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            K200CaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, expression, compat, diagnostics)
    }
}
