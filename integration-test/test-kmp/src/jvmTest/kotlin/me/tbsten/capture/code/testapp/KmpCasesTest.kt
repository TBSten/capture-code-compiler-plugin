package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case101.Snippets_KmpCase101
import me.tbsten.capture.code.testapp.case102.Snippets_KmpCase102
import me.tbsten.capture.code.testapp.case104.Platform_KmpCase104
import me.tbsten.capture.code.testapp.case105.Snippets_KmpCase105
import me.tbsten.capture.code.testapp.kmp103.Platform_KmpCase103

// ============================================================================
// KMP 検証テストのカタログ (task-021 採用版: 再配置版 2026-05-14)。
//
// marker / use site / capturedSources<T>() 呼び出しを **すべて test sourceset**
// に配置する設計に統一した (本ファイル含め main 系には KMP 検証用 fixture 無し)。
//
//   commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }
//   commonTest → { jsTest, wasmJsTest, mingwX64Test, appleTest* }
//
// 各ケースの配置:
//   #101 commonTest に marker + use site → jvmTest から `capturedSources<T>()` 呼び出し
//   #102 commonTest に marker + shared use site / jvmTest に jvm-only use site
//   #103 commonTest に marker + annotated expect / jvmTest に annotated actual /
//        他 test sourceset に annotation 無し actual (compile 用)
//   #104 commonTest に marker + annotation 無し expect / jvmTest に annotated
//        actual / 他 test sourceset に annotation 無し actual (compile 用)
//   #105 commonTest に marker / `jvmLinuxTest` (intermediate) に use site
//
// 全 5 ケースを `jvmTest` から実行する (kotest junit5 runner が JVM 専用のため)。
// task-020 / task-022 / task-023 / task-024 と統合して本 task-021 で再配置完了。
// ============================================================================

class KmpCasesTest : StringSpec({

    "ケース101: commonTest で marker 定義 + commonTest の use site (KMP 基本)" {
        // 期待: jvm target test compile で commonTest の use site 1 件をキャプチャ。
        capturedSources<Snippets_KmpCase101>() shouldBe listOf(
            Snippets_KmpCase101(
                source = Source(value = "internal fun kmpCase101_shared() = \"from commonTest\""),
            ),
        )
    }

    "ケース102: commonTest marker + commonTest と jvmTest で use site (jvm target)" {
        // 期待: jvm target test compile では commonTest (shared) と jvmTest (jvmOnly) の
        // 両 source set が可視のため 2 件キャプチャされる。
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
        // KNOWN-LIMITATION (2026-05-14): K2 IR の expect/actual マッチングで、 同一 compilation
        // invocation 内に actual が存在する場合 **expect 宣言は IR module fragment から消去** され、
        // expect 側に付いた marker annotation は IR phase に到達しない。 結果として Logic B
        // (IR collector) は actual 側 (annotation 付き) のみをキャプチャできる。
        //
        //   →  design §7.6 の「expect + actual 両 annotated → 2 件」は本実装では達成不能。
        //      ケース #104 (actual のみ annotated → 1 件) と同じ観測結果になる。
        //      plugin 側では追加対応せず Known Limitations として固定化する方針 (2026-05-14 確定)。
        //      詳細は design 文書 §13 Known Limitations §13.3 参照。
        //
        // 本テストは「現実の K2 IR の挙動」を退行検知するための regression test として、
        // 1 件 (actual のみ) が確実にキャプチャされることを検証する。
        // marker `Platform_KmpCase103` は source + location filler を持つため、
        // location.filePath 等は test fixture 実行環境依存。 source value と filePath の
        // 末尾セグメント、 packageName のみを strict に検証する。
        val captured = capturedSources<Platform_KmpCase103>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(
            value = "internal actual fun kmpCase103_currentTimeMillis(): Long = System.currentTimeMillis()",
        )
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp.kmp103"
        // capture 元が jvmTest sourceset 由来であることを verify (source set hierarchy 整合性)
        check(captured[0].location.filePath.contains("jvmTest")) {
            "case103 capture filePath should contain 'jvmTest' but was: ${captured[0].location.filePath}"
        }
    }

    "ケース104: actual のみに annotation (expect は無印)" {
        // 期待: jvm target で jvmTest の actual 1 件のみがキャプチャされる。
        // expect は annotation 無しなので Logic B の収集対象外。
        // compiler-plugin-design.md §7.6 「actual 側を 1 件キャプチャ」シナリオの実機検証。
        capturedSources<Platform_KmpCase104>() shouldBe listOf(
            Platform_KmpCase104(
                source = Source(value = "internal actual fun kmpCase104_platformName(): String = \"JVM\""),
            ),
        )
    }

    "ケース105: source set hierarchy (intermediate な jvmLinuxTest)" {
        // 期待: jvm target で jvmLinuxTest (intermediate) は可視のためキャプチャされる。
        // `commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }` の階層により、
        // linuxX64 target test からも同じ use site が見える (本 ticket は jvmTest で検証)。
        val captured = capturedSources<Snippets_KmpCase105>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "internal fun kmpCase105_jvmOrLinuxOnly() = \"jvm+linux\"")
        // SourceLocation の filePath が jvmLinuxTest 由来であることを verify (intermediate hierarchy 検証)
        check(captured[0].location.filePath.contains("jvmLinuxTest")) {
            "case105 capture filePath should contain 'jvmLinuxTest' but was: ${captured[0].location.filePath}"
        }
    }
})
