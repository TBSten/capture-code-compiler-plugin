package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// ============================================================================
// ケース101: commonMain で marker 定義 + commonMain の use site (KMP 基本)
// 本テストでは jvmTest 内でローカルに marker / site を宣言して挙動を検証する。
// 実環境では commonMain に置く想定 (.local/test-cases.md 参照)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase101(val source: Source = Source())

@Snippets_KmpCase101
fun kmpCase101_shared() = "from commonMain"

// ============================================================================
// ケース102: commonMain marker + commonMain と jvmMain で use site
// 同じく jvmTest 内でローカル simulate。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase102(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Snippets_KmpCase102
fun kmpCase102_shared() = "common"

@Snippets_KmpCase102
fun kmpCase102_jvmOnly() = "jvm"

// ============================================================================
// ケース103: expect / actual 両方に annotation を付与
// jvmTest 内で simulate (expect/actual 本体は本格的 KMP 構成が必要)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase103(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Platform_KmpCase103
fun kmpCase103_currentTimeMillisActual(): Long = System.currentTimeMillis()

// ============================================================================
// ケース104: actual のみに annotation
// jvmTest 内で simulate
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase104(val source: Source = Source())

@Platform_KmpCase104
fun kmpCase104_platformNameJvm(): String = "JVM"

// ============================================================================
// ケース105: source set hierarchy (intermediate な jvmAndroidMain)
// jvmTest 内で simulate
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_KmpCase105(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Snippets_KmpCase105
fun kmpCase105_jvmOrAndroidOnly() = "jvm+android"

class KmpCasesTest : StringSpec({

    "ケース101: commonMain で marker 定義 + commonMain の use site (KMP 基本)".config(enabled = false) {
        capturedSources<Snippets_KmpCase101>() shouldBe listOf(
            Snippets_KmpCase101(source = Source(value = "fun kmpCase101_shared() = \"from commonMain\"")),
        )
    }

    "ケース102: commonMain marker + commonMain と jvmMain で use site (jvm target)".config(enabled = false) {
        // 期待: jvm target ビルドでは common と jvm の両方が出てくる
        val captured = capturedSources<Snippets_KmpCase102>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "fun kmpCase102_shared() = \"common\"")
        captured[1].source shouldBe Source(value = "fun kmpCase102_jvmOnly() = \"jvm\"")
    }

    "ケース103: expect / actual 両方に annotation を付与".config(enabled = false) {
        // 期待: expect と actual の両方をキャプチャ
        val captured = capturedSources<Platform_KmpCase103>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(
            value = "fun kmpCase103_currentTimeMillisActual(): Long = System.currentTimeMillis()",
        )
    }

    "ケース104: actual のみに annotation (expect は無印)".config(enabled = false) {
        capturedSources<Platform_KmpCase104>() shouldBe listOf(
            Platform_KmpCase104(source = Source(value = "fun kmpCase104_platformNameJvm(): String = \"JVM\"")),
        )
    }

    "ケース105: source set hierarchy (intermediate な jvmAndroidMain)".config(enabled = false) {
        // 期待: jvm target で jvmAndroidMain は可視のためキャプチャされる
        val captured = capturedSources<Snippets_KmpCase105>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun kmpCase105_jvmOrAndroidOnly() = \"jvm+android\"")
    }
})
