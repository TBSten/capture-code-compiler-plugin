// Kotlin 2.4.0-RC: FirChecker 系 base class が DeprecatedForRemovalCompilerApi opt-in required に
// なり、 また FirRegularClassSymbol#fir 等が SymbolInternals opt-in 必要となった。
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
)

package me.tbsten.capture.code.compat.k240rc.checker

import me.tbsten.capture.code.compat.k240rc.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.4.0-RC baseline 向けの **Logic G** entry point (Java shim dispatcher)。
 *
 * task-119: ロジック本体は main module の [ValidateCapturedSourcesCall] に統一された。
 */
public object K240RcCapturedSourcesCallCheckerLogic {
    private val logic = ValidateCapturedSourcesCall()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            K240RcCaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
    ) {
        logic(context, reporter, expression, compat, diagnostics)
    }
}
