// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.compat.k230.k230Compat
import me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite.CollectExpressionSite
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions.ExtractMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.ValidateMarkerAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect
import me.tbsten.capture.code.compat.k230.CompatContextImpl
import org.jetbrains.kotlin.descriptors.ClassKind
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
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.3.x baseline 向けの `FirAdditionalCheckersExtension` 群 + 各 logic dispatcher。
 *
 * task-124 で旧 `K230CaptureCodeMarkerClassChecker.kt` / `K230MarkerAnnotationChecker.kt` /
 * `K230CapturedSourcesCallChecker.kt` / `K230ExpressionSiteCollector.kt` の 4 dispatcher
 * file を本 file に集約。 各 `K230*CheckerLogic` object は Java shim
 * (`K230*Shim.java`) から `INSTANCE.run(...)` で呼ばれる。
 *
 * Kotlin 2.2.x で `FirRegularClassChecker` / `FirBasicExpressionChecker` 等が typealias
 * (= `FirDeclarationChecker<...>` / `FirExpressionChecker<...>`) になったため、 Java shim は
 * 元クラスを継承し、 ここでは underlying generic 型として登録する。
 *
 * task-119 follow-up: 各 dispatcher で `CompatContextImpl()` を 3 個 new していた状態を
 * module-scoped singleton ([k230Compat]) で共有する形に統一。
 */

// ============================================================================
// Logic dispatcher objects (Java shim から呼ばれる)
// ============================================================================

/** Logic A — marker class 発見 / registry 登録。 */
public object K230CaptureCodeMarkerClassCheckerLogic {
    @JvmStatic
    public fun run(
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
        declaration: FirRegularClass,
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
public object K230MarkerAnnotationCheckerLogic {
    private val logic = ValidateMarkerAnnotation()
    private val warnIfOverrideNoEffect = WarnIfOverrideNoEffect()
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
        logic(context, reporter, declaration, k230Compat, diagnostics)
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
public object K230CapturedSourcesCallCheckerLogic {
    private val logic = ValidateCapturedSourcesCall()
    private val diagnostics = object : ValidateCapturedSourcesCall.Diagnostics {
        override val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String> =
            CompatContextImpl.K230Diagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE
    }

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
    ) {
        logic(context, reporter, expression, k230Compat, diagnostics)
    }
}

/** Logic B-fir — expression site 収集 (file-level annotation も含む)。 */
public object K230ExpressionSiteCollectorLogic {
    private val logic = CollectExpressionSite()

    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirStatement,
    ) {
        logic(context, reporter, expression, k230Compat)
    }
}

// ============================================================================
// FirAdditionalCheckersExtension 群 (CompatContextImpl から登録される)
// ============================================================================

internal class K230MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K230CaptureCodeMarkerClassCheckerShim.INSTANCE)
    }
}

internal class K230MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K230MarkerAnnotationCheckerShim.INSTANCE)
    }
}

internal class K230CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K230CapturedSourcesCallCheckerShim.INSTANCE)
    }
}

internal class K230ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirExpressionChecker<FirStatement>> =
            setOf(K230ExpressionSiteCollectorShim.INSTANCE)
    }
}
