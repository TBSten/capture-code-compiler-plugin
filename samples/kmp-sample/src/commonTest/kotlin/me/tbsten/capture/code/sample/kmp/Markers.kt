package me.tbsten.capture.code.sample.kmp

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// KMP sample: marker annotations (commonTest 配置)
//
// design §13 Known Limitations に従い、 marker / use site / `capturedSources<T>()`
// 呼び出しを **すべて test sourceset** に置く。 commonTest に marker を宣言し、
// commonTest または jvmTest の use site で利用、 jvmTest から `capturedSources` を
// 呼んで検証する (test sourceset 完結方式)。
// ============================================================================

/** Case A: 最小構成 (Source filler のみ)。 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KmpSnippet(
    val source: Source = Source(),
)

/** Case B: SourceLocation も含む。 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KmpDetailed(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

/** Case C: user-defined parameter (タグ付け)。 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KmpFeature(
    val name: String,
    val source: Source = Source(),
)
