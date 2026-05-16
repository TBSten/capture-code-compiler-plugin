package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.compat.k202.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.0.21 baseline 向けの **Logic G** checker (entry point)。
 *
 * task-119: ロジック本体は main module の [ValidateCapturedSourcesCall] に統一された。
 */
internal object K202CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    private val logic = ValidateCapturedSourcesCall()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            CompatContextImpl.K202Diagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, expression, compat, diagnostics)
    }
}
