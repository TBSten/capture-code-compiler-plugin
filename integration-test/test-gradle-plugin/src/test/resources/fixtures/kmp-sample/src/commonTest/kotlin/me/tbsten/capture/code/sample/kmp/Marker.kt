package me.tbsten.capture.code.sample.kmp

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

/**
 * KMP commonTest 起点の最小 marker annotation fixture。
 *
 * use site は同じ commonTest source set の `Usage.kt` に配置し、
 * `capturedSources<KmpSnippet>()` 呼び出し側 (`jvmTest`) が jvm target test compile
 * 時に IR rewrite 経由でこの marker の use site をすべて拾うことを verify する。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KmpSnippet(
    val source: Source = Source(),
)
