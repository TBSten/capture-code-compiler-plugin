package me.tbsten.capture.code.testapp.case105

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

/**
 * ケース #105 (source set hierarchy) 検証用の marker annotation。
 *
 * commonMain で定義し、intermediate source set `jvmLinuxMain` の use site を
 * leaf target (jvm / linuxX64) からキャプチャ可能にする。
 *
 *   commonMain → jvmLinuxMain → { jvmMain, linuxX64Main }
 *   commonMain → { jsMain, wasmJsMain, mingwX64Main, appleMain* }
 *
 * - jvm / linuxX64 target: `jvmLinuxMain` のサイトが可視 → キャプチャされる
 * - js / wasmJs / mingwX64 / apple target: `jvmLinuxMain` は不可視 → emptyList()
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase105(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)
