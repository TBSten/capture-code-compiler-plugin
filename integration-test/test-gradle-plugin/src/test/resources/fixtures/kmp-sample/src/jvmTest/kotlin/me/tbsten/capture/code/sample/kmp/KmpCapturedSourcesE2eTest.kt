package me.tbsten.capture.code.sample.kmp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.capturedSources

/**
 * KMP project に `plugins { id("me.tbsten.capture.code") }` 経由で plugin を attach
 * し、 commonTest 起点の `@KmpSnippet` use site が jvm target test compile で
 * IR rewrite される (= `capturedSources<KmpSnippet>()` が空でない結果を返す) ことを
 * 真の E2E で verify する fixture test。
 *
 * `:integration-test:test-gradle-plugin:KmpE2eTest` から TestKit 経由でこの test が
 * jvmTest として実行される。
 */
class KmpCapturedSourcesE2eTest : StringSpec({
    "commonTest の @KmpSnippet use site が jvmTest 経由でキャプチャされる" {
        val captured = capturedSources<KmpSnippet>()

        // commonTest に 2 件の use site (kmpCommonGreet / kmpCommonFarewell) を配置
        // しているので、 jvm target test compile では両方が拾える想定。
        captured.size shouldBe 2
        captured.map { it.source.value }.toSet() shouldBe setOf(
            "internal fun kmpCommonGreet(): String = \"Hello from commonTest!\"",
            "internal fun kmpCommonFarewell(): String = \"Goodbye from commonTest!\"",
        )
    }
})
