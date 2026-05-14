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
// ## ステータス (post-completion polish, 2026-05-14)
// 本ファイルの 2 ケース (dedent=true / dedent=false) は、 構造上 `:integration-test:test-jvm`
// では検証できない。 理由:
//   - `:integration-test:test-jvm` は `kotlinCompilerPluginClasspath(project(":compiler-plugin"))`
//     で compiler plugin を直接 attach しており、 `:gradle-plugin` の `CaptureCodeExtension`
//     (`captureCode { dedent = ... }`) は経由しない。
//   - 同一の test-jvm モジュール内で「dedent=true」 と「dedent=false」 の **両方** を同時に
//     検証するには 2 度コンパイルする必要があるが、 単一 module の build script からは不可能。
//
// 代替として、 task-040 で構築した `:integration-test:test-gradle-plugin` の Gradle TestKit
// fixture (`dedent-sample/`) 経由で **ユーザ実利用形態 (`plugins { id("me.tbsten.capture.code") }`
// + `captureCode { dedent = ... }`)** で同シナリオを検証している:
//
//   integration-test/test-gradle-plugin/src/test/kotlin/.../DslOptionsE2eTest.kt
//     - 「dedent=true (デフォルト) ではインデントが除去される」
//     - 「dedent=false ではインデントが保持される」
//
// したがって本ファイルの 2 ケースは **意図的に disabled のまま** 保持し、 disable された
// 設計上の理由を残すドキュメントとして機能させる。 実 carrier は test-gradle-plugin 側。
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
    //
    //   実 carrier:
    //     :integration-test:test-gradle-plugin
    //       me.tbsten.capture.code.gradle.DslOptionsE2eTest
    //         "dedent=true (デフォルト) ではインデントが除去される"
    //
    //   本ケースは test-jvm の `kotlinCompilerPluginClasspath` 経路では DSL option が
    //   届かないため意図的に disabled。 上記 TestKit fixture で `plugins { id("me.tbsten.capture.code") }
    //   + captureCode { dedent = true }` を実コンパイルで検証している。
    // ------------------------------------------------------------------
    "dedent=true (デフォルト) ではインデントが除去される".config(enabled = false) {
        // 実体は :integration-test:test-gradle-plugin の DslOptionsE2eTest を参照。
        val captured = capturedSources<DslOptionsMarker>()
        captured.size shouldBe 1
        captured[0].source.value shouldBe "fun indented_member(): String {\n    return \"hello\"\n}"
    }

    // ------------------------------------------------------------------
    // dedent option: false にすると元のインデントが残る
    //
    //   実 carrier:
    //     :integration-test:test-gradle-plugin
    //       me.tbsten.capture.code.gradle.DslOptionsE2eTest
    //         "dedent=false ではインデントが保持される"
    //
    //   本ケースは test-jvm の `kotlinCompilerPluginClasspath` 経路では DSL option が
    //   届かないため意図的に disabled。 上記 TestKit fixture で
    //   `captureCode { dedent = false }` を実コンパイルで検証している。
    // ------------------------------------------------------------------
    "dedent=false ではインデントが保持される".config(enabled = false) {
        // 実体は :integration-test:test-gradle-plugin の DslOptionsE2eTest を参照。
        val captured = capturedSources<DslOptionsMarker>()
        captured.size shouldBe 1
        captured[0].source.value shouldBe
            "    fun indented_member(): String {\n        return \"hello\"\n    }"
    }
})
