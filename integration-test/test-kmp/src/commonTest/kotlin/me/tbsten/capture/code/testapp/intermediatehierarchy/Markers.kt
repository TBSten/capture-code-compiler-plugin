package me.tbsten.capture.code.testapp.intermediatehierarchy

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// source set hierarchy (intermediate な jvmLinuxTest) シナリオ
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker は commonTest、annotated use site は intermediate test sourceset
//   `jvmLinuxTest` に配置。`commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }`
//   の階層により、jvmTest / linuxX64Test の両 compilation から intermediate の
//   use site が可視となり 1 件キャプチャされる。 js / wasmJs / mingwX64 では
//   jvmLinuxTest が不可視のため capture 0 件 (test 検証は jvmTest で行う)。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class IntermediateHierarchyMarker(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
