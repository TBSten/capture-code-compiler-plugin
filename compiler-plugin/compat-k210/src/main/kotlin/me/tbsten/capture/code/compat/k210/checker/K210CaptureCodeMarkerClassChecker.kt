package me.tbsten.capture.code.compat.k210.checker

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
 * Kotlin 2.1.x baseline 向けの **Logic A** checker (marker class 発見 / registry 登録)。
 *
 * task-072 で `:compiler-plugin` main module から compat-k210 layer に移動した版。 K200 版と
 * 機械的コピー。
 */
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
        val options = CaptureCodeMarkerOptionsExtractor.extract(captureCodeAnnotation)
        if (options == CaptureCodeMarkerOptions.DEFAULT) {
            CaptureCodeMarkerRegistry.registerMarker(fqn)
        } else {
            CaptureCodeMarkerRegistry.registerMarkerOptions(fqn, options)
        }
    }
}
