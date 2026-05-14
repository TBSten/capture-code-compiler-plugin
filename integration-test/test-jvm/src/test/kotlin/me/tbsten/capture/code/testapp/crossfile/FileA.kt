package me.tbsten.capture.code.testapp.crossfile

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_CrossFile(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Snippets_CrossFile
fun crossFromA() = "A"
