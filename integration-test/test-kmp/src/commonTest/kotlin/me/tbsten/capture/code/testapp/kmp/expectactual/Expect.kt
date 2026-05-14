package me.tbsten.capture.code.testapp.kmp.expectactual

// expect / actual 両 annotated シナリオ: expect 側にも annotation を付ける (commonTest)
@ExpectActualBothMarker
internal expect fun expectActualCurrentTimeMillis(): Long
