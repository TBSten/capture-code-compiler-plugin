package me.tbsten.capture.code.testapp.scenarios.ordered

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class OrderedSnippet(val source: Source = Source())

@OrderedSnippet
fun orderedA1() {}

@OrderedSnippet
fun orderedA2() {}
