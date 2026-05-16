package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k202.CompatContextImpl
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

/**
 * Kotlin 2.0.21 baseline 向けの **Logic F** marker annotation 制約違反診断 checker (entry point)。
 *
 * task-119: ロジック本体は main module の [ValidateMarkerAnnotation] に統一された。
 * task-123: warning side ([WarnIfOverrideNoEffect]) を chain で実行する。
 */
internal object K202MarkerAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
    private val compat = CompatContextImpl()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K202Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K202Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K202Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }
    private val warningDiagnostics = object : WarnIfOverrideNoEffect.Diagnostics {
        override val markerOverrideNoEffect: KtDiagnosticFactory1<String> =
            CompatContextImpl.K202Diagnostics.CC_MARKER_OVERRIDE_NO_EFFECT
    }

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
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
