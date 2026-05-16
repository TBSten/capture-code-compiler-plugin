package me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.feature.markerDefinition.diffFromDefault
import me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions.ExtractMarkerOptions
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation

/**
 * Logic F (warning side): emit `CC_MARKER_OVERRIDE_NO_EFFECT` when one or more
 * `@CaptureCode(...)` override arguments evaluate to the **same** value as the
 * active plugin's global default configuration.
 *
 * Runs in the FIR phase as a sibling of `ValidateMarkerAnnotation` so the
 * warning surfaces at marker declaration time rather than at every capture
 * call site. The actual override extraction is delegated to
 * [ExtractMarkerOptions] (drift-free, lives in main).
 *
 * task-123: introduced. Wire-up: each `K{XXX}MarkerAnnotationChecker` invokes
 * [invoke] alongside the existing Logic F validation, supplying its own
 * `KtDiagnosticFactory1<String>` for `CC_MARKER_OVERRIDE_NO_EFFECT` via
 * [Diagnostics].
 */
public class WarnIfOverrideNoEffect {

    /**
     * One-element diagnostic surface so each `compat-kXXX` can plug its own
     * `KtDiagnosticFactory1<String>` (for `CC_MARKER_OVERRIDE_NO_EFFECT`)
     * without main carrying any version-bound factory reference.
     */
    public interface Diagnostics {
        public val markerOverrideNoEffect: KtDiagnosticFactory1<String>
    }

    private val extractMarkerOptions = ExtractMarkerOptions()

    /**
     * Visits every `@CaptureCode`-meta annotation on [declaration] and reports
     * `CC_MARKER_OVERRIDE_NO_EFFECT` once per annotation that has any
     * redundant override.
     *
     * If the marker has no overrides at all (`CaptureCodeMarkerOptions.DEFAULT`)
     * this is a no-op; the warning is only relevant when the user wrote an
     * explicit override.
     */
    public operator fun invoke(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        declaration: FirRegularClass,
        globalConfig: CaptureCodePluginConfig,
        diagnostics: Diagnostics,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        val session = context.session
        for (annotation in declaration.annotations) {
            if (!annotation.isCaptureCodeMeta(session)) continue
            reportOnAnnotation(annotation, globalConfig, reporter, context, diagnostics)
        }
    }

    private fun FirAnnotation.isCaptureCodeMeta(session: FirSession): Boolean =
        toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId

    private fun reportOnAnnotation(
        annotation: FirAnnotation,
        globalConfig: CaptureCodePluginConfig,
        reporter: DiagnosticReporter,
        context: CheckerContext,
        diagnostics: Diagnostics,
    ) {
        val markerOptions = extractMarkerOptions(annotation)
        if (markerOptions == CaptureCodeMarkerOptions.DEFAULT) return
        val redundantKeys = markerOptions.diffFromDefault(globalConfig)
        if (redundantKeys.isEmpty()) return
        reporter.reportOn(
            annotation.source,
            diagnostics.markerOverrideNoEffect,
            redundantKeys.joinToString(),
            context,
        )
    }
}
