package me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass

import me.tbsten.capture.code.compat.CompatContext
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
 *
 * task-127: register 時に source file path も渡すように拡張。 [CaptureCodeMarkerRegistry]
 * 側で registration 履歴として保持され、 IR phase の
 * [me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn.WarnIfDuplicateMarkerFqn]
 * が duplicate FQN 検出時の warning location に利用する。
 */
public class DiscoverMarkerClass {
    private val extractMarkerOptions = ExtractMarkerOptions()

    public operator fun invoke(
        context: CheckerContext,
        declaration: FirRegularClass,
        compat: CompatContext? = null,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return

        val captureCodeAnnotation = declaration.annotations.firstOrNull { annotation ->
            annotation.toAnnotationClassId(context.session) == CaptureCodeMetaAnnotation.classId
        } ?: return

        val classId = declaration.symbol.classId
        val fqn = classId.asSingleFqName().asString()
        val options = extractMarkerOptions(captureCodeAnnotation)
        val sourceFilePath = compat?.containingFilePathOf(context)
        if (options == CaptureCodeMarkerOptions.DEFAULT) {
            CaptureCodeMarkerRegistry.registerMarker(fqn, sourceFilePath)
        } else {
            CaptureCodeMarkerRegistry.registerMarkerOptions(fqn, options, sourceFilePath)
        }
    }
}
