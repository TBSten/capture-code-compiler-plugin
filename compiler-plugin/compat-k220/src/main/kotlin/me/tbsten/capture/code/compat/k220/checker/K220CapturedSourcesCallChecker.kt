// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi opt-in required に
// なり、 また FirRegularClassSymbol#fir 等が SymbolInternals opt-in 必要となった。
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
)

package me.tbsten.capture.code.compat.k220.checker

import me.tbsten.capture.code.compat.k220.CompatContextImpl
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.2.x baseline 向けの **Logic G** entry point (Java shim dispatcher)。
 *
 * task-119: ロジック本体は main module の [ValidateCapturedSourcesCall] に統一された。
 * 本 object は K220 固有の [K220CaptureCodeDiagnostics] を渡して main logic を呼ぶだけの
 * dispatcher として機能する。
 */
public object K220CapturedSourcesCallCheckerLogic {
    private val logic = ValidateCapturedSourcesCall()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            K220CaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
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
