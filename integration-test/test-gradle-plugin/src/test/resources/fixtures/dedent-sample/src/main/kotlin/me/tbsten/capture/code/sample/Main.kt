package me.tbsten.capture.code.sample

import me.tbsten.capture.code.capturedSources

/**
 * dedent option の挙動を `Source.value` の生文字列として stdout に出力する。
 * GradleRunner test 側で stdout を assert する。
 *
 * 出力フォーマット:
 *   TEST_RESULT_BEGIN
 *   <Base64 of Source.value>     # 改行/インデントが visible にならない問題回避のため Base64 で出す
 *   TEST_RESULT_END
 */
fun main() {
    val captured = capturedSources<IndentedMarker>()
    println("TEST_RESULT_BEGIN")
    println("count=${captured.size}")
    captured.forEach { snippet ->
        val raw = snippet.source.value
        val encoded = java.util.Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        println("source_b64=$encoded")
    }
    println("TEST_RESULT_END")
}
