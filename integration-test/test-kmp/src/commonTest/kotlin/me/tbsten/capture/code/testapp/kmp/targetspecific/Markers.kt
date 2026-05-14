package me.tbsten.capture.code.testapp.kmp.targetspecific

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// commonTest marker + commonTest と jvmTest で use site (target 別の結果)
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker は commonTest、shared use site も commonTest、jvm-only use site は
//   jvmTest に配置。jvmTest compilation では commonTest + jvmTest の両方が可視
//   なので 2 件キャプチャされる。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TargetSpecificKmpMarker(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
