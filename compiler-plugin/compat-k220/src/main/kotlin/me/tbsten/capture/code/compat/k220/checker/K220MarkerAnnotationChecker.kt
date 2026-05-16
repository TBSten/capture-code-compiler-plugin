// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k220.checker

import me.tbsten.capture.code.compat.k220.CompatContextImpl
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

/**
 * Kotlin 2.2.x baseline 向けの **Logic F** entry point (Java shim dispatcher)。
 *
 * Kotlin 2.2.x で `FirDeclarationChecker.check` が context-parameter signature
 * (`context(...) fun check(D)`) に切り替わり、 root KGP 2.0.0 で compile する本 module の
 * Kotlin source からは override を書けない。 そのため checker 本体は Java shim
 * ([K220MarkerAnnotationCheckerShim]) で `FirRegularClassChecker` を継承し、
 * 本オブジェクトに dispatch する。
 *
 * task-119: 検査ロジック本体は main module の [ValidateMarkerAnnotation] に統一された。
 * 本 object は K220 固有の [CompatContextImpl.K220Diagnostics] を渡して main logic を呼ぶだけの
 * dispatcher として機能する。
 */
public object K220MarkerAnnotationCheckerLogic {
    private val logic = ValidateMarkerAnnotation()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K220Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
    ) {
        logic(context, reporter, declaration, compat, diagnostics)
    }
}
