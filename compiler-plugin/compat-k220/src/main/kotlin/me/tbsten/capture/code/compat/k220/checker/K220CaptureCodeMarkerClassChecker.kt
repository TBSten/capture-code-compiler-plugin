// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k220.checker

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions.ExtractMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId

/**
 * Kotlin 2.2.x baseline 向けの **Logic A** ロジック本体 (marker class 発見 / registry 登録)。
 *
 * Kotlin 2.2.x で `FirDeclarationChecker.check` の abstract signature が
 * `context(...) fun check(D)` に切り替わったため、 root KGP 2.0.0 で compile する本 module
 * では override を Kotlin 側で書けない。 そのため checker 本体は Java shim
 * ([K220CaptureCodeMarkerClassCheckerShim]) で `FirRegularClassChecker` を継承し、 ここに
 * ロジック本体を切り出してデリゲートする。
 *
 * K200 / K210 版と機能は完全同一。
 */
public object K220CaptureCodeMarkerClassCheckerLogic {
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
