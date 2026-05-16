package me.tbsten.capture.code.compat.k210.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k210.CompatContextImpl
import me.tbsten.capture.code.compat.k210.k210Compat
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions.ExtractMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.1.x baseline 向けの **FIR Checker 群 + FirAdditionalCheckersExtension 群**。
 *
 * task-124 で旧 4 checker file (`K210CaptureCodeMarkerClassChecker.kt` /
 * `K210MarkerAnnotationChecker.kt` / `K210CapturedSourcesCallChecker.kt` /
 * `K210ExpressionSiteCollector.kt`) を本 file に集約。
 *
 * task-119 follow-up: 各 checker で `CompatContextImpl()` を 3 個 new していた状態を
 * module-scoped singleton ([k210Compat]) で共有する形に統一。
 */

// ============================================================================
// FIR Checker 群 (Logic A / F / G / B-fir に対応)
// ============================================================================

/** Logic A — marker class 発見 / registry 登録。 */
internal object K210CaptureCodeMarkerClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return

        val captureCodeAnnotation = declaration.annotations.firstOrNull { annotation ->
            annotation.toAnnotationClassId(context.session) == CaptureCodeMetaAnnotation.classId
        } ?: return

        val classId = declaration.symbol.classId
        val fqn = classId.asSingleFqName().asString()
        val options = ExtractMarkerOptions()(captureCodeAnnotation)
        if (options == CaptureCodeMarkerOptions.DEFAULT) {
            CaptureCodeMarkerRegistry.registerMarker(fqn)
        } else {
            CaptureCodeMarkerRegistry.registerMarkerOptions(fqn, options)
        }
    }
}

/** Logic F — marker annotation 検査 + warning chain (CC_MARKER_OVERRIDE_NO_EFFECT)。 */
internal object K210MarkerAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K210Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K210Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K210Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }
    private val warningDiagnostics = object : WarnIfOverrideNoEffect.Diagnostics {
        override val markerOverrideNoEffect: KtDiagnosticFactory1<String> =
            CompatContextImpl.K210Diagnostics.CC_MARKER_OVERRIDE_NO_EFFECT
    }

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, declaration, k210Compat, diagnostics)
        warnIfOverrideNoEffect(
            context,
            reporter,
            declaration,
            CaptureCodePluginConfigHolder.get(),
            warningDiagnostics,
        )
    }
}

/** Logic G — `capturedSources<T>()` 呼び出しの型引数検査。 */
internal object K210CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    private val logic = ValidateCapturedSourcesCall()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            CompatContextImpl.K210Diagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, expression, k210Compat, diagnostics)
    }
}

/** Logic B-fir — expression site 収集 (file-level annotation も含む)。 */
internal object K210ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    private val logic = CollectExpressionSite()

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        logic(context, reporter, expression, k210Compat)
    }
}

// ============================================================================
// FirAdditionalCheckersExtension 群 (CompatContextImpl から登録される)
// ============================================================================

internal class K210MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K210CaptureCodeMarkerClassChecker)
    }
}

internal class K210MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K210MarkerAnnotationChecker)
    }
}

internal class K210CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K210CapturedSourcesCallChecker)
    }
}

internal class K210ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(K210ExpressionSiteCollector)
    }
}
