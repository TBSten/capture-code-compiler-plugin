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
 * ## KMP fixture のスコープ
 * 当初は JVM + KMP の 2 fixture を予定していたが、 KMP fixture の
 * `:jvmTest` で `capturedSources<T>()` の `error("...is not applied")` が rewrite
 * されない問題が判明した (commonTest 起点の use site が IR transform の rewrite を
 * 受けないケース)。 `:gradle-plugin` DSL 経由の attach パスと、 KMP の commonTest →
 * jvmTest hierarchy 間の compilation 配線の整合性に関わる可能性があるため、 本モジュール
 * のスコープ外として別途調査する。 既存の `:integration-test:test-kmp:jvmTest` で
 * KMP の主要シナリオは CI で継続検証されているので、 KMP の真の E2E カバレッジは保たれている。
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
