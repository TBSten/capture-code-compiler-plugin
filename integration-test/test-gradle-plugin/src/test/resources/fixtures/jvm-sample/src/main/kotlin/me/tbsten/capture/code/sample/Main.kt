package me.tbsten.capture.code.sample

import me.tbsten.capture.code.capturedSources

/**
 * task-040 fixture: marker でマークした全 use site を `capturedSources<T>()` で集めて
 * stdout に出力する。 GradleRunner test 側で stdout の内容を assert する。
 *
 * 出力フォーマットは「TEST_RESULT_BEGIN .. TEST_RESULT_END」で囲み、 `./gradlew run` の
 * 他の出力に紛れても安定して切り出せるようにする。
 */
fun main() {
    println("TEST_RESULT_BEGIN")
    capturedSources<Snippet>().forEach { snippet ->
        println("source=${snippet.source.value}")
    }
    println("TEST_RESULT_END")
}
