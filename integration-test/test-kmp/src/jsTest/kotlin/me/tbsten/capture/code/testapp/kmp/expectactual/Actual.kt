package me.tbsten.capture.code.testapp.expectactual

// expect / actual 両 annotated シナリオ: 他 target の compile を成立させるための annotation **無し** actual。
// js target test では capturedSources<ExpectActualBothMarker>() の検証は行わない。
internal actual fun expectActualCurrentTimeMillis(): Long = 0L
