package me.tbsten.capture.code.testapp

import me.tbsten.capture.code.captureCode

fun main() {
    // compiler plugin が runtime API 呼び出しを差し替える想定。
    // 現在は plugin に変換ロジックがないため、annotation モジュールのデフォルト実装 ("") が返る。
    val captured: String = captureCode()
    check(captured.isEmpty()) { "Default captureCode() should return empty string, got: '$captured'" }
    println("CaptureCode integration test (JVM): OK")
}
