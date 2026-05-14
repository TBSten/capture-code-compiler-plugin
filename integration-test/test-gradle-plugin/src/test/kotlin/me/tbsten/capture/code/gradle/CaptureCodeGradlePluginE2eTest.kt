package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain as collectionShouldContain
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

/**
 * Gradle TestKit + fixture project による真の E2E テスト。
 *
 * fixture project (`src/test/resources/fixtures/jvm-sample/`) を `GradleRunner` で
 * 起動し、 ユーザ実利用形態 (`plugins { id("me.tbsten.capture.code") }`) で compiler
 * plugin が attach されることを verify する。
 *
 * ## 検証内容
 *   1. plugin apply + `:assemble` が success (Kotlin compile が走ること)
 *   2. `:run` (application plugin) で main を実行し、 capturedSources<Snippet> の
 *      結果が stdout に出力されること
 *
 * ## 役割分担
 *   - `:gradle-plugin:test` = ProjectBuilder ベースの DSL 配線高速 sanity
 *   - 本 test = TestKit + 実 Gradle build によるユーザ実利用形態の真の E2E
 *
 * ## KMP fixture
 * task-070 で KMP fixture (`kmp-sample`) を別 spec (`KmpE2eTest`) として追加済。
 * 当初 task-040 で観測した「commonTest 起点 `capturedSources<T>()` が rewrite され
 * ない」事象は再現せず、 `:gradle-plugin` DSL apply 経路でも KMP project の jvm
 * target test compile で IR rewrite が機能することが verify された。
 *
 * ## 速度
 * TestKit が起動する子 Gradle build は 1 件あたり 20-90 秒。
 */
class CaptureCodeGradlePluginE2eTest : StringSpec({

    val fixturesDir: File = run {
        val sysProp = System.getProperty("test-gradle-plugin.fixturesDir")
        if (sysProp != null) {
            File(sysProp)
        } else {
            // IDE 等 system property が未設定の場合の fallback。 Gradle test runtime
            // からは build.gradle.kts で system property を渡している。
            File("src/test/resources/fixtures").absoluteFile
        }
    }
    val jvmSampleDir: File = File(fixturesDir, "jvm-sample")

    // fixture 側 settings.gradle.kts に Kotlin version を渡すための Gradle property。
    // build.gradle.kts (本モジュール) で root の libs.versions.toml から system property
    // として注入されている。
    val kotlinVersion: String = System.getProperty("test-gradle-plugin.kotlinVersion")
        ?: error("test-gradle-plugin.kotlinVersion system property is not set")

    fun newRunner(projectDir: File, vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(
            *args,
            "-Ptest-gradle-plugin.kotlinVersion=$kotlinVersion",
            "--stacktrace",
            "--no-configuration-cache",
        )
        .forwardOutput()

    // Test 実行毎に SUCCESS / UP-TO-DATE どちらでも accept する。 UP-TO-DATE は
    // 前回 build の cache が効いている場合、 同じ outcome として扱える。
    val successOutcomes = setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)

    "jvm-sample fixture: :assemble が success (plugin apply + compile)" {
        val result = newRunner(jvmSampleDir, ":assemble").build()

        successOutcomes.collectionShouldContain(result.task(":compileKotlin")?.outcome)
        successOutcomes.collectionShouldContain(result.task(":assemble")?.outcome)
    }

    "jvm-sample fixture: :run の出力に capturedSources の結果が含まれる" {
        // :run は application plugin が提供。 Main.kt が capturedSources<Snippet>() を
        // 集めて stdout に出力する。
        val result = newRunner(jvmSampleDir, ":run").build()

        successOutcomes.collectionShouldContain(result.task(":run")?.outcome)

        val output = result.output
        output shouldContain "TEST_RESULT_BEGIN"
        output shouldContain "TEST_RESULT_END"
        // marker 付き関数の本文がそのまま source として埋まる最小シナリオ。
        // includeAnnotationLines = false (default) のため `@Snippet` 行は含まれない、
        // 関数宣言行から始まる文字列が入る。
        output shouldContain "fun greet()"
        output shouldContain "\"Hello!\""
        output shouldContain "fun farewell()"
        output shouldContain "\"Goodbye!\""
    }
})
