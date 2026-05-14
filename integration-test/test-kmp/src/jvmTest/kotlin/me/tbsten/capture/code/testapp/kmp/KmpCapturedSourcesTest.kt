package me.tbsten.capture.code.testapp.kmp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.kmp.actualonly.ActualOnlyMarker
import me.tbsten.capture.code.testapp.kmp.commonbasic.CommonBasicMarker
import me.tbsten.capture.code.testapp.kmp.expectactual.ExpectActualBothMarker
import me.tbsten.capture.code.testapp.kmp.intermediatehierarchy.IntermediateHierarchyMarker
import me.tbsten.capture.code.testapp.kmp.targetspecific.TargetSpecificKmpMarker

// ============================================================================
// KMP 検証テストのカタログ (test sourceset 完結配置版 2026-05-14)。
//
// marker / use site / capturedSources<T>() 呼び出しを **すべて test sourceset**
// に配置する設計に統一した (本ファイル含め main 系には KMP 検証用 fixture 無し)。
//
//   commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }
//   commonTest → { jsTest, wasmJsTest, mingwX64Test, appleTest* }
//
// 各シナリオの配置:
//   common basic           commonTest に marker + use site → jvmTest から `capturedSources<T>()` 呼び出し
//   target specific        commonTest に marker + shared use site / jvmTest に jvm-only use site
//   expect/actual both     commonTest に marker + annotated expect / jvmTest に annotated actual /
//                          他 test sourceset に annotation 無し actual (compile 用)
//   actual only            commonTest に marker + annotation 無し expect / jvmTest に annotated
//                          actual / 他 test sourceset に annotation 無し actual (compile 用)
//   intermediate hierarchy commonTest に marker / `jvmLinuxTest` (intermediate) に use site
//
// 全 5 シナリオを `jvmTest` から実行する (kotest junit5 runner が JVM 専用のため)。
// ============================================================================

class KmpCapturedSourcesTest : StringSpec({

    "commonTest で marker + use site (KMP 基本)" {
        // 期待: jvm target test compile で commonTest の use site 1 件をキャプチャ。
        capturedSources<CommonBasicMarker>() shouldBe listOf(
            CommonBasicMarker(
                source = Source(value = "internal fun commonBasicShared() = \"from commonTest\""),
            ),
        )
    }

    "commonTest marker + commonTest と jvmTest で use site (jvm target で 2 件)" {
        // 期待: jvm target test compile では commonTest (shared) と jvmTest (jvmOnly) の
        // 両 source set が可視のため 2 件キャプチャされる。
        // compiler-plugin-design.md §7.6 「plugin は target ごとに走り、各 target
        // から可視な source set のサイトをキャプチャする」シナリオの実機検証。
        val captured = capturedSources<TargetSpecificKmpMarker>()
        captured.size shouldBe 2
        captured.map { it.source.value }.toSet() shouldBe setOf(
            "internal fun targetSpecificShared() = \"common\"",
            "internal fun targetSpecificJvmOnly() = \"jvm\"",
        )
    }

    "expect / actual 両方に annotation を付与 (K2 IR の挙動を Known Limitation として固定)" {
        // KNOWN-LIMITATION (2026-05-14): K2 IR の expect/actual マッチングで、 同一 compilation
        // invocation 内に actual が存在する場合 **expect 宣言は IR module fragment から消去** され、
        // expect 側に付いた marker annotation は IR phase に到達しない。 結果として Logic B
        // (IR collector) は actual 側 (annotation 付き) のみをキャプチャできる。
        //
        //   →  design §7.6 の「expect + actual 両 annotated → 2 件」は本実装では達成不能。
        //      「actual のみ annotated → 1 件」シナリオと同じ観測結果になる。
        //      plugin 側では追加対応せず Known Limitations として固定化する方針 (2026-05-14 確定)。
        //      詳細は design 文書 §13 Known Limitations §13.3 参照。
        //
        // 本テストは「現実の K2 IR の挙動」を退行検知するための regression test として、
        // 1 件 (actual のみ) が確実にキャプチャされることを検証する。
        // marker `ExpectActualBothMarker` は source + location filler を持つため、
        // location.filePath 等は test fixture 実行環境依存。 source value と filePath の
        // 末尾セグメント、 packageName のみを strict に検証する。
        val captured = capturedSources<ExpectActualBothMarker>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(
            value = "internal actual fun expectActualCurrentTimeMillis(): Long = System.currentTimeMillis()",
        )
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp.kmp.expectactual"
        // capture 元が jvmTest sourceset 由来であることを verify (source set hierarchy 整合性)
        check(captured[0].location.filePath.contains("jvmTest")) {
            "expect/actual both capture filePath should contain 'jvmTest' but was: ${captured[0].location.filePath}"
        }
    }

    "actual のみに annotation (expect は無印)" {
        // 期待: jvm target で jvmTest の actual 1 件のみがキャプチャされる。
        // expect は annotation 無しなので Logic B の収集対象外。
        // compiler-plugin-design.md §7.6 「actual 側を 1 件キャプチャ」シナリオの実機検証。
        capturedSources<ActualOnlyMarker>() shouldBe listOf(
            ActualOnlyMarker(
                source = Source(value = "internal actual fun actualOnlyPlatformName(): String = \"JVM\""),
            ),
        )
    }

    "source set hierarchy (intermediate な jvmLinuxTest からの capture)" {
        // 期待: jvm target で jvmLinuxTest (intermediate) は可視のためキャプチャされる。
        // `commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }` の階層により、
        // linuxX64 target test からも同じ use site が見える (本テストは jvmTest で検証)。
        val captured = capturedSources<IntermediateHierarchyMarker>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "internal fun intermediateHierarchyJvmOrLinuxOnly() = \"jvm+linux\"")
        // SourceLocation の filePath が jvmLinuxTest 由来であることを verify (intermediate hierarchy 検証)
        check(captured[0].location.filePath.contains("jvmLinuxTest")) {
            "intermediate hierarchy capture filePath should contain 'jvmLinuxTest' but was: ${captured[0].location.filePath}"
        }
    }
})
