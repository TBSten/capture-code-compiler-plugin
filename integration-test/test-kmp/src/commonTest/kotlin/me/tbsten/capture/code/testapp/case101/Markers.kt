package me.tbsten.capture.code.testapp.case101

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

// ============================================================================
// ケース101: commonMain で marker 定義 + commonMain の use site (KMP 基本)
//
// 配置方針 (test sourceset 完結 2026-05-14):
//   marker / use site / capturedSources<T>() 呼び出しを **すべて test sourceset**
//   に配置する。本 marker は commonTest 内で定義し、commonTest の use site と
//   jvmTest の capturedSources 呼び出しで使う。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase101(val source: Source = Source())
