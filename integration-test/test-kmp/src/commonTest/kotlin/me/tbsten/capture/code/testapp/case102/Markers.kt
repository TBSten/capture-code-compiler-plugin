package me.tbsten.capture.code.testapp.case102

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// ケース102: commonTest marker + commonTest と jvmTest で use site (target 別の結果)
//
// 採用版 (task-021 再配置版 2026-05-14):
//   marker は commonTest、shared use site も commonTest、jvm-only use site は
//   jvmTest に配置。jvmTest compilation では commonTest + jvmTest の両方が可視
//   なので 2 件キャプチャされる。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase102(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
