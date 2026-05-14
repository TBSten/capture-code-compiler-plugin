package me.tbsten.capture.code.testapp.case104

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

// ============================================================================
// ケース104: actual のみに annotation (expect は無印)
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker / expect (annotation 無し) は commonTest、annotated actual は
//   jvmTest に配置。他 test sourceset には annotation 無しの actual を配置して
//   compile を成立させる。
//
// jvmTest compilation では actual (jvmTest) のみが annotated なので 1 件
// キャプチャされる (expect は annotation 無しなので Logic B の収集対象外)。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase104(val source: Source = Source())
