package me.tbsten.capture.code.testapp.kmp103

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// ケース103: expect / actual 両方に annotation を付与
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker / annotated expect は commonTest、annotated actual は jvmTest に
//   配置。他の test sourceset (jsTest / wasmJsTest / linuxX64Test / mingwX64Test)
//   には annotation **無し** の actual を置いて compile を成立させる。
//
// jvmTest compilation では expect (commonTest) + actual (jvmTest) の両方が
// annotated なので 2 件キャプチャされる。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase103(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
