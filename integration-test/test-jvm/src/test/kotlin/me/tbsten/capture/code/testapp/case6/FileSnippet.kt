@file:SnippetFile_Case6

package me.tbsten.capture.code.testapp.case6

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source

@CaptureCode
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SnippetFile_Case6(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

val case6_a = 1
val case6_b = 2
