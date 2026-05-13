package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-018 (Gradle plugin DSL options): `captureCode { ... }` で渡した option が
// 実コンパイル時に CompilerConfiguration まで届くことを示す integration-test。
//
// 本 ticket では Gradle plugin の **配線のみ** を実装するため、実 option を消費する
// path (task-015 dedent / task-016 includeImports / task-013 includeLineInfo)
// が完了するまでは値が反映されない。各シナリオは `.config(enabled = false)` で
// 用意し、task-015 完了後に enable する。
//
// task-026 (KMP integration test) では Gradle TestKit 経由で `captureCode { ... }`
// の DSL を実際に評価するシナリオを別途追加する。本ファイルは現行の
// `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` 構成下で、
// `freeCompilerArgs += listOf("-P", "plugin:me.tbsten.capture.code:dedent=false")`
// を build.gradle.kts に書いた場合の挙動を将来検証することを意図する。
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DslOptionsMarker(val source: Source = Source())

class DslOptionsCase_Outer {
    @DslOptionsMarker
    fun indented_member(): String {
        return "hello"
    }
}

class DslOptionsTest : StringSpec({

    // ------------------------------------------------------------------
    // dedent option: デフォルト (true) ではインデントが除去される
    //   実消費は task-015 (Logic D) 完了後に有効化する。
    // ------------------------------------------------------------------
    "dedent=true (デフォルト) ではインデントが除去される".config(enabled = false) {
        // 本 case は task-015 完了後に enable する想定。
        // task-015 が `CaptureCodePluginConfig.dedent` を読んで SourceNormalizer に渡し、
        // declaration capture が dedent を適用すれば、以下の期待値で PASS する。
        val captured = capturedSources<DslOptionsMarker>()
        captured.size shouldBe 1
        captured[0].source.value shouldBe "fun indented_member(): String {\n    return \"hello\"\n}"
    }

    // ------------------------------------------------------------------
    // dedent option: false にすると元のインデントが残る
    //   Gradle DSL からは `captureCode { dedent = false }` で指定。
    //   現在の integration-test build.gradle.kts は :gradle-plugin を経由しないため、
    //   freeCompilerArgs を経由した CLI option 渡しの確認は task-026 (TestKit) で行う。
    // ------------------------------------------------------------------
    "dedent=false ではインデントが保持される".config(enabled = false) {
        // 本 case は task-015 (実消費) + Gradle TestKit (task-026) の両方が揃って enable する。
        // 期待挙動: dedent を切ったとき、`@DslOptionsMarker` の付いた関数のインデントが
        // そのまま残る (= 各行頭に 4 spaces が残る)。
        val captured = capturedSources<DslOptionsMarker>()
        captured.size shouldBe 1
        captured[0].source.value shouldBe
            "    fun indented_member(): String {\n        return \"hello\"\n    }"
    }
})
