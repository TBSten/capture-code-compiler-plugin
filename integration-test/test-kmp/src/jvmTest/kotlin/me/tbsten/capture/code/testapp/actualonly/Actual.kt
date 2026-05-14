package me.tbsten.capture.code.testapp.actualonly

// actualOnly シナリオ: jvmTest の actual のみに annotation を付ける
@ActualOnlyMarker
internal actual fun actualOnlyPlatformName(): String = "JVM"
