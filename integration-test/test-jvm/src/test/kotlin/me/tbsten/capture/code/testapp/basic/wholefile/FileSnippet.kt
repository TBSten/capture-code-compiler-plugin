@file:SnippetFile_WholeFile

package me.tbsten.capture.code.testapp.basic.wholefile

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source

@CaptureCode
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SnippetFile_WholeFile(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

val wholeFileA = 1
val wholeFileB = 2
