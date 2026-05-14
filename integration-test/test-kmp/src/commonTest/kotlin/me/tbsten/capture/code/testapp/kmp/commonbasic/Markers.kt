package me.tbsten.capture.code.testapp.kmp.commonbasic

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

// ============================================================================
// commonMain で marker 定義 + commonMain の use site (KMP 基本シナリオ)
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker / use site / capturedSources<T>() 呼び出しを **すべて test sourceset**
//   に配置する。本 marker は commonTest 内で定義し、commonTest の use site と
//   jvmTest の capturedSources 呼び出しで使う。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CommonBasicMarker(val source: Source = Source())
