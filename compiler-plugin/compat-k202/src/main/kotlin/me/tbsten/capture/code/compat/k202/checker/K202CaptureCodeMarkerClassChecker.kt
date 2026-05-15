package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.compat.CaptureCodeMarkerOptions
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.fir.marker.CaptureCodeMarkerOptionsExtractor
import me.tbsten.capture.code.fir.marker.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId

/**
 * Kotlin 2.0.x baseline 向けの **Logic A** checker (marker class 発見 / registry 登録)。
 *
 * task-072 で `:compiler-plugin` main module の `CaptureCodeMarkerClassChecker` を compat-k202
 * layer に移動した版。 marker 発見時は [CaptureCodeMarkerRegistry] (compat module の
 * process-scoped holder) に直接登録する。
 *
 * `CaptureCodeFirMarkerService` (session component) は本 ticket で **廃止** され、 marker option
 * の extraction は [CaptureCodeMarkerOptionsExtractor] (compat 共有ヘルパ) に委譲する。
 * これにより compat-k202 / compat-k210 で重複コードを最小化する。
 */
internal object K202CaptureCodeMarkerClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
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
        val options = CaptureCodeMarkerOptionsExtractor.extract(captureCodeAnnotation)
        if (options == CaptureCodeMarkerOptions.DEFAULT) {
            CaptureCodeMarkerRegistry.registerMarker(fqn)
        } else {
            CaptureCodeMarkerRegistry.registerMarkerOptions(fqn, options)
        }
    }
}
