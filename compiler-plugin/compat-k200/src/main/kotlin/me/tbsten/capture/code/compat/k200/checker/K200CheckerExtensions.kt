package me.tbsten.capture.code.compat.k200.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k200.CompatContextImpl
import me.tbsten.capture.code.compat.k200.k200Compat
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.DiscoverMarkerClass
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
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
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.0.x baseline 向けの **FIR Checker 群 + FirAdditionalCheckersExtension 群**。
 *
 * task-124 で旧 `K200CaptureCodeMarkerClassChecker.kt` / `K200MarkerAnnotationChecker.kt` /
 * `K200CapturedSourcesCallChecker.kt` / `K200ExpressionSiteCollector.kt` の 4 checker
 * file を本 file に集約。 各 checker は K2.0 baseline の `check(declaration, context, reporter)`
 * signature を直接 override する (Java shim 不要)。
 *
 * task-119 follow-up: 各 checker で `CompatContextImpl()` を 3 個 new していた状態を
 * module-scoped singleton ([k200Compat]) で共有する形に統一。
 */

// ============================================================================
// FIR Checker 群 (Logic A / F / G / B-fir に対応)
// ============================================================================

/** Logic A — marker class 発見 / registry 登録。 */
internal object K200CaptureCodeMarkerClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    private val discoverMarkerClass = DiscoverMarkerClass()

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
    ) {
        discoverMarkerClass(context, declaration)
    }
}

/** Logic F — marker annotation 検査 + warning chain (CC_MARKER_OVERRIDE_NO_EFFECT)。 */
internal object K200MarkerAnnotationChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K200Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K200Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K200Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }
    private val warningDiagnostics = object : WarnIfOverrideNoEffect.Diagnostics {
        override val markerOverrideNoEffect: KtDiagnosticFactory1<String> =
            CompatContextImpl.K200Diagnostics.CC_MARKER_OVERRIDE_NO_EFFECT
    }

    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, declaration, k200Compat, diagnostics)
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
internal object K200CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    private val logic = ValidateCapturedSourcesCall()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            CompatContextImpl.K200Diagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        logic(context, reporter, expression, k200Compat, diagnostics)
    }
}

/** Logic B-fir — expression site 収集 (file-level annotation も含む)。 */
internal object K200ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    private val logic = CollectExpressionSite()

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        logic(context, reporter, expression, k200Compat)
    }
}

// ============================================================================
// FirAdditionalCheckersExtension 群 (CompatContextImpl から登録される)
// ============================================================================

internal class K200MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K200CaptureCodeMarkerClassChecker)
    }
}

internal class K200MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K200MarkerAnnotationChecker)
    }
}

internal class K200CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K200CapturedSourcesCallChecker)
    }
}

internal class K200ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(K200ExpressionSiteCollector)
    }
}
