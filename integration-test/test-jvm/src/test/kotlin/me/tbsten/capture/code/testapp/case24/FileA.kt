package me.tbsten.capture.code.testapp.case24

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case24(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Snippets_Case24
fun case24_fromA() = "A"
