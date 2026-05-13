package me.tbsten.capture.code.testapp

import me.tbsten.capture.code.captureCode

fun main() {
    val captured: String = captureCode()
    check(captured.isEmpty()) { "Default captureCode() should return empty string, got: '$captured'" }
    println("CaptureCode integration test (KMP): OK")
}
