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
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の FIR class checker) は以下を保証する責務がある。
 * いずれも違反した場合は invoke が silently no-op で返り、 marker 登録は行われない
 * (= fail-fast はしないが、 後段 logic で diagnostic に発展しうる)。
 *
 * - `declaration` は FIR-resolved な `FirRegularClass` (signature 上保証)。
 * - `declaration.classKind` は `ClassKind.ANNOTATION_CLASS` であることが期待される。
 *   そうでない場合は冒頭で early return する (= caller が誤って ordinary class
 *   を渡したケースでも crash しない設計)。
 * - `declaration.annotations` は FIR resolution phase 完了済 (= `toAnnotationClassId(session)`
 *   が解決可能)。 typical root cause: caller が FIR resolution 完了前の declaration
 *   を渡している (= compiler-plugin 内部の phase 順序の bug)。
 * - `declaration.symbol.classId` が non-null (= top-level / nested annotation class
 *   なら必ず存在する)。 K1.x の local annotation class のように classId 不在の
 *   case は K2 では nominal に発生しない。
 *
 * `require(...)` での fail-fast は導入していない (= 想定外 input でも silent
 * no-op のほうが、 IR phase 側で error を発火させる現行の error 経路を阻害しない)。
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
