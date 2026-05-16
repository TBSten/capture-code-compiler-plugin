package me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions.ExtractMarkerOptions
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId

/**
 * Logic A: marker class discovery & registration.
 *
 * Finds classes annotated with a `@CaptureCode`-meta annotation,
 * extracts their options, and registers them in the compilation-scoped
 * [CaptureCodeMarkerRegistry] for use by IR phase checkers.
 */
public class DiscoverMarkerClass {
    private val extractMarkerOptions = ExtractMarkerOptions()

    public operator fun invoke(
        context: CheckerContext,
        declaration: FirRegularClass,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return

        val captureCodeAnnotation = declaration.annotations.firstOrNull { annotation ->
            annotation.toAnnotationClassId(context.session) == CaptureCodeMetaAnnotation.classId
        } ?: return

        val classId = declaration.symbol.classId
        val fqn = classId.asSingleFqName().asString()
        val options = extractMarkerOptions(captureCodeAnnotation)
        if (options == CaptureCodeMarkerOptions.DEFAULT) {
            CaptureCodeMarkerRegistry.registerMarker(fqn)
        } else {
            CaptureCodeMarkerRegistry.registerMarkerOptions(fqn, options)
        }
    }
}
