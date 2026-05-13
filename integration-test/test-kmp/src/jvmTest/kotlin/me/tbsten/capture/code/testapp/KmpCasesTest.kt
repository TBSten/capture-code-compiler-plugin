package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case101.Snippets_KmpCase101
import me.tbsten.capture.code.testapp.case102.Snippets_KmpCase102
import me.tbsten.capture.code.testapp.case105.Snippets_KmpCase105
import me.tbsten.capture.code.testapp.kmp103.Platform_KmpCase103

// ============================================================================
// KMP 検証テストのカタログ。
//
// task-019 で sample project skeleton (jvm / js / wasmJs / linuxX64 / mingwX64
// および opt-in な ios / macos) は整備済。本ファイルは依然 jvmTest 内で marker /
// 使用箇所を **simulate** している暫定形であり、各シナリオの **enable** と
// commonMain / 各 target source set への **実配置** は後続 ticket で実施する:
//
//   - ケース #101 → task-020 (commonMain marker + commonMain use site) ✅ 実配置済
//   - ケース #102 → task-021 (target ごとの結果) ✅ 実配置済
//   - ケース #103 → task-022 (expect + actual 両方 annotated) ✅ 実配置済
//   - ケース #104 → task-023 (actual のみ annotated)
//   - ケース #105 → task-024 (source set hierarchy / intermediate source set)
//
// 各 ticket では (a) simulate marker を削除して commonMain (or 該当 source set)
// に移動、(b) `.config(enabled = false)` を外す、(c) 期待値を確認する。
// ============================================================================

// ============================================================================
// ケース101: commonMain で marker 定義 + commonMain の use site (KMP 基本)
// marker / use site は commonMain (case101/Markers.kt + case101/Usage.kt) に
// 配置されている。task-020 でこの形に移行した。
// ============================================================================

// ============================================================================
// ケース102: commonMain marker + commonMain と jvmMain で use site
// marker は commonMain (case102/Markers.kt) に、shared use site は commonMain
// (case102/Common.kt) に、jvm-only use site は jvmMain (case102/Jvm.kt) に
// 配置されている。task-021 でこの形に移行した。
// jvm target compile では commonMain + jvmMain の両 source set が可視のため
// 2 件キャプチャされる。js / wasmJs / native target は jvmMain が不可視のため
// shared 1 件のみキャプチャされる (compile success が保証される)。
// ============================================================================

// ============================================================================
// ケース103: expect / actual 両方に annotation を付与
// marker / expect は commonMain (kmp103/Markers.kt + kmp103/Expect.kt) に、
// actual は jvmMain (kmp103/Actual.kt) に配置されている。task-022 でこの形に移行した。
// 他 target (js / wasmJs / linuxX64 / mingwX64) には annotation 無しの actual を
// 配置して compile success を保つ (本 ticket scope は jvm target のみ)。
// ============================================================================

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
// marker / use site は intermediate source set `jvmLinuxMain` (case105/Shared.kt)
// と commonMain (case105/Markers.kt) に配置されている。task-024 でこの形に移行した。
// 本プロジェクトに android target が無いため `jvmAndroidMain` の代替として
// `jvmLinuxMain` (jvm + linuxX64 の親) を使用する。
// ============================================================================

class KmpCasesTest : StringSpec({

    "ケース101: commonMain で marker 定義 + commonMain の use site (KMP 基本)".config(enabled = false) {
        capturedSources<Snippets_KmpCase101>() shouldBe listOf(
            Snippets_KmpCase101(source = Source(value = "fun kmpCase101_shared() = \"from commonMain\"")),
        )
    }

    "ケース102: commonMain marker + commonMain と jvmMain で use site (jvm target)" {
        // 期待: jvm target ビルドでは common と jvm の両方が出てくる。
        // compiler-plugin-design.md §7.6 「plugin は target ごとに走り、各 target
        // から可視な source set のサイトをキャプチャする」シナリオの実機検証。
        val captured = capturedSources<Snippets_KmpCase102>()
        captured.size shouldBe 2
        captured.map { it.source.value }.toSet() shouldBe setOf(
            "internal fun kmpCase102_shared() = \"common\"",
            "internal fun kmpCase102_jvmOnly() = \"jvm\"",
        )
    }

    "ケース103: expect / actual 両方に annotation を付与" {
        // 期待: jvm target で commonMain の expect 宣言と jvmMain の actual 宣言を独立にキャプチャ。
        // compiler-plugin-design.md §7.6 「expect / actual 独立カウント」シナリオの実機検証。
        //
        // キャプチャ順序は IR 走査順 (file 単位) で決まるため、source value で expect / actual を
        // 識別してから verify する (順序非依存)。
        val captured = capturedSources<Platform_KmpCase103>()
        captured.size shouldBe 2

        val expectCapture = captured.singleOrNull {
            it.source.value == "internal expect fun kmpCase103_currentTimeMillis(): Long"
        }
        val actualCapture = captured.singleOrNull {
            it.source.value ==
                "internal actual fun kmpCase103_currentTimeMillis(): Long = System.currentTimeMillis()"
        }

        // expect 側 (commonMain) と actual 側 (jvmMain) の 2 件が揃っていること
        check(expectCapture != null) { "expect side capture not found in $captured" }
        check(actualCapture != null) { "actual side capture not found in $captured" }

        // SourceLocation の filePath が commonMain と jvmMain でそれぞれ異なる source set 由来であることを verify
        check(expectCapture.location.filePath.contains("commonMain")) {
            "expect capture filePath should contain 'commonMain' but was: ${expectCapture.location.filePath}"
        }
        check(actualCapture.location.filePath.contains("jvmMain")) {
            "actual capture filePath should contain 'jvmMain' but was: ${actualCapture.location.filePath}"
        }
    }

    "ケース104: actual のみに annotation (expect は無印)".config(enabled = false) {
        capturedSources<Platform_KmpCase104>() shouldBe listOf(
            Platform_KmpCase104(source = Source(value = "fun kmpCase104_platformNameJvm(): String = \"JVM\"")),
        )
    }

    "ケース105: source set hierarchy (intermediate な jvmAndroidMain)" {
        // 期待: jvm target で jvmLinuxMain (intermediate) は可視のためキャプチャされる
        val captured = capturedSources<Snippets_KmpCase105>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun kmpCase105_jvmOrAndroidOnly() = \"jvm+android\"")
    }
})
