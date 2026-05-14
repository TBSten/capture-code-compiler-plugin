package me.tbsten.capture.code.testapp.expectactual

// expect / actual 両 annotated シナリオ: jvmTest の actual にも annotation を付ける
@ExpectActualBothMarker
internal actual fun expectActualCurrentTimeMillis(): Long = System.currentTimeMillis()
