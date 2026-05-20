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
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の `K{XXX}MarkerAnnotationChecker`) は以下を保証する責務がある。
 * いずれも違反した場合は invoke が silently no-op で返り、 warning は出ない設計のため、
 * `require(...)` での fail-fast は導入していない (= override 設定の SSR ミスで marker
 * 動作が止まるよりは silent skip の方が safer)。
 *
 * - `declaration` は FIR-resolved な `FirRegularClass`。 `classKind` が
 *   `ANNOTATION_CLASS` でない場合は invoke 冒頭で early-return する。
 * - `declaration.annotations` の中に `@CaptureCode`-meta annotation が 1 個以上
 *   含まれること。 含まれない場合は per-annotation の `continue` で no-op。
 * - `globalConfig: CaptureCodePluginConfig` は `CommandLineProcessor` が
 *   `CaptureCodePluginOptionsHolder` 経由で publish したものを caller が取得して
 *   渡すこと。 typical root cause: holder の `compute()` が呼ばれる前に invoke
 *   された (= compiler-plugin の phase 順序 bug)。 silent fallback では
 *   `CaptureCodePluginConfig` の DEFAULT (= override 全て `Default`) と diff
 *   されるため、 「明示 `Yes` / `No` だけが redundant 判定される」 経路に倒れる。
 * - `diagnostics.markerOverrideNoEffect` は caller の `K{XXX}CaptureCodeDiagnostics`
 *   から取得した `KtDiagnosticFactory1<String>` (= renderer も含めて registered)。
 * - `extractMarkerOptions` (= 内部 `ExtractMarkerOptions`) は drift-free。 同一
 *   marker class に対して invoke を複数回呼んでも結果は deterministic。
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
