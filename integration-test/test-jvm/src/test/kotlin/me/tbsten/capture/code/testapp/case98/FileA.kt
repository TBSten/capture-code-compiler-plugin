package me.tbsten.capture.code.testapp.case98

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case98(val source: Source = Source())

@Snippets_Case98
fun case98_a1() {
}

@Snippets_Case98
fun case98_a2() {
}
