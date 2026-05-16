// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k220.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k220.CompatContextImpl
import me.tbsten.capture.code.compat.k220.k220Compat
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.DiscoverMarkerClass
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.2.x baseline 向けの `FirAdditionalCheckersExtension` 群 + 各 logic dispatcher。
 *
 * task-124 で旧 `K220CaptureCodeMarkerClassChecker.kt` / `K220MarkerAnnotationChecker.kt` /
 * `K220CapturedSourcesCallChecker.kt` / `K220ExpressionSiteCollector.kt` の 4 dispatcher
 * file を本 file に集約。 各 `K220*CheckerLogic` object は Java shim
 * (`K220*Shim.java`) から `INSTANCE.run(...)` で呼ばれる。
 *
 * task-119 follow-up: 各 dispatcher で `CompatContextImpl()` を 3 個 new していた状態を
 * module-scoped singleton ([k220Compat]) で共有する形に統一。
 */

// ============================================================================
// Logic dispatcher objects (Java shim から呼ばれる)
// ============================================================================

/** Logic A — marker class 発見 / registry 登録。 */
public object K220CaptureCodeMarkerClassCheckerLogic {

    private val discoverMarkerClass = DiscoverMarkerClass()

    @JvmStatic
    public fun run(
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
        declaration: FirRegularClass,
    ) {
        discoverMarkerClass(context, declaration)
    }
}

/** Logic F — marker annotation 検査 + warning chain (CC_MARKER_OVERRIDE_NO_EFFECT)。 */
public object K220MarkerAnnotationCheckerLogic {
    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
    private val diagnostics = object : ValidateMarkerAnnotation.Diagnostics {
        override val markerIsExpect: KtDiagnosticFactory0 =
            CompatContextImpl.K220Diagnostics.CC_MARKER_IS_EXPECT
        override val markerParameterTypeInvalid: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_MARKER_PARAMETER_TYPE_INVALID
        override val markerFillerRequiresDefault: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT
    }
    private val warningDiagnostics = object : WarnIfOverrideNoEffect.Diagnostics {
        override val markerOverrideNoEffect: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_MARKER_OVERRIDE_NO_EFFECT
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
    ) {
        logic(context, reporter, declaration, k220Compat, diagnostics)
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
public object K220CapturedSourcesCallCheckerLogic {
    private val logic = ValidateCapturedSourcesCall()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            CompatContextImpl.K220Diagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
    ) {
        logic(context, reporter, expression, k220Compat, diagnostics)
    }
}

/** Logic B-fir — expression site 収集 (file-level annotation も含む)。 */
public object K220ExpressionSiteCollectorLogic {
    private val logic = CollectExpressionSite()

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirStatement,
    ) {
        logic(context, reporter, expression, k220Compat)
    }
}

// ============================================================================
// FirAdditionalCheckersExtension 群 (CompatContextImpl から登録される)
// ============================================================================

internal class K220MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K220CaptureCodeMarkerClassCheckerShim.INSTANCE)
    }
}

internal class K220MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K220MarkerAnnotationCheckerShim.INSTANCE)
    }
}

internal class K220CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K220CapturedSourcesCallCheckerShim.INSTANCE)
    }
}

internal class K220ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirExpressionChecker<FirStatement>> =
            setOf(K220ExpressionSiteCollectorShim.INSTANCE)
    }
}
