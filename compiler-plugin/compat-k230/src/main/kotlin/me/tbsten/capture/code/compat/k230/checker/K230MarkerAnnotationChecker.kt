// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k230.CompatContextImpl
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

/**
 * Kotlin 2.3.x baseline 向けの **Logic F** entry point (Java shim dispatcher)。
 *
 * task-119: ロジック本体は main module の [ValidateMarkerAnnotation] に統一された。
 * task-123: warning side ([WarnIfOverrideNoEffect]) を chain で実行する。
 */
public object K230MarkerAnnotationCheckerLogic {
    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K230Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K230Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K230Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }
    private val warningDiagnostics = object : WarnIfOverrideNoEffect.Diagnostics {
        override val markerOverrideNoEffect: KtDiagnosticFactory1<String> =
            CompatContextImpl.K230Diagnostics.CC_MARKER_OVERRIDE_NO_EFFECT
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
    ) {
        logic(context, reporter, declaration, compat, diagnostics)
        warnIfOverrideNoEffect(
            context,
            reporter,
            declaration,
            CaptureCodePluginConfigHolder.get(),
            warningDiagnostics,
        )
    }
}
