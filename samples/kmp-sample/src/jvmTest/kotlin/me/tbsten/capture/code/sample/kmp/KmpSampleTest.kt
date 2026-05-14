package me.tbsten.capture.code.sample.kmp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// KMP sample test: commonTest に置いた marker / use site を jvmTest 側から
// `capturedSources<T>()` で集めて検証する。
//
// design §13 Known Limitations に従う 「test sourceset 完結方式」 のため、
// kotest junit5 runner (jvm 専用) からまとめて検証する形を取る。 全 target で
// compile が成功すれば他 target でも plugin は動作している (test-kmp の
// KmpCapturedSourcesTest で別途 verify 済み)。
// ============================================================================

class KmpSampleTest : StringSpec({

    "KmpSnippet — Source filler のみの marker をキャプチャ" {
        val captured = capturedSources<KmpSnippet>()
        captured.map { it.source.value }
            .shouldContainExactlyInAnyOrder(
                "internal fun kmpSampleAdd(a: Int, b: Int): Int = a + b",
                "internal fun kmpSampleMul(a: Int, b: Int): Int = a * b",
            )
    }

    "KmpDetailed — Source + SourceLocation + CaptureKind を全部埋める" {
        val captured = capturedSources<KmpDetailed>()
        captured.size shouldBe 1
        val d = captured.single()
        d.source shouldBe Source(value = "internal fun kmpSampleHello(): String = \"hello from common\"")
        d.location.packageName shouldBe "me.tbsten.capture.code.sample.kmp"
        // commonTest 由来であることを確認
        d.location.filePath shouldContain "commonTest"
        // line info が埋まっていること (具体値は宣言位置依存のため正の整数チェックのみ)
        (d.location.startLine > 0) shouldBe true
        (d.location.endLine >= d.location.startLine) shouldBe true
    }

    "KmpFeature — user-defined `name` parameter は値を保持し source も埋まる" {
        val captured = capturedSources<KmpFeature>()
        captured.size shouldBe 2
        val byName = captured.associateBy { it.name }
        byName.keys.shouldContainExactlyInAnyOrder("dark-mode", "experimental-search")
        byName["dark-mode"]!!.source.value shouldBe "internal fun featureDarkMode(): Boolean = true"
        byName["experimental-search"]!!.source.value shouldBe
            "internal fun featureExperimentalSearch(): Boolean = false"
    }
})
