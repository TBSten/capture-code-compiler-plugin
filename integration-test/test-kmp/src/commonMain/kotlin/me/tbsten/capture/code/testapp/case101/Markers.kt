package me.tbsten.capture.code.testapp.case101

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

/**
 * ケース #101: commonMain で marker 定義 + commonMain の use site (KMP 基本)
 *
 * marker を commonMain に置くことで `internal` 可視性が同一 module 内の全 target
 * (jvmMain / jsMain / wasmJsMain / linuxX64Main / mingwX64Main / appleMain) から
 * 参照可能になる。compiler-plugin-design.md §7.6 参照。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase101(val source: Source = Source())
