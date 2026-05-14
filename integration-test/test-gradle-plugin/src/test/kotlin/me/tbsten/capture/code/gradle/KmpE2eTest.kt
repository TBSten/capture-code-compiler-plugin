package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

/**
 * KMP project に対する真の E2E テスト。
 *
 * fixture project (`src/test/resources/fixtures/kmp-sample/`) を `GradleRunner` で
 * 起動し、 ユーザ実利用形態 (`plugins { id("me.tbsten.capture.code") }` を KMP
 * project に apply) で compiler plugin が attach され、 commonTest 起点の
 * `@KmpSnippet` use site が jvm target test compile で IR rewrite されることを
 * verify する。
 *
 * ## 役割分担
 *   - `:integration-test:test-kmp:jvmTest`
 *       direct `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` attach 経路の KMP 検証
 *   - 本 test
 *       `:gradle-plugin` 経由の `plugins { id("me.tbsten.capture.code") }` apply 経路の KMP 検証
 *
 * task-040 で scope 外にした「KMP project + DSL 経由 plugin apply で commonTest 起点
 * の `capturedSources<T>()` が IR rewrite されない疑惑」を本テストが回帰検出する。
 *
 * ## 速度
 * TestKit が起動する子 Gradle build は 1 件あたり 30-120 秒。 KMP は jvm target の
 * test compile + run まで含むので JVM-only fixture より長くなる。
 */
class KmpE2eTest : StringSpec({

    val fixturesDir: File = run {
        val sysProp = System.getProperty("test-gradle-plugin.fixturesDir")
        if (sysProp != null) {
            File(sysProp)
        } else {
            File("src/test/resources/fixtures").absoluteFile
        }
    }
    val kmpSampleDir: File = File(fixturesDir, "kmp-sample")

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

    val successOutcomes = setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)

    "kmp-sample fixture: :jvmTest が success (KMP + plugin apply + commonTest 起点 capturedSources)" {
        val result = newRunner(kmpSampleDir, ":jvmTest").build()

        successOutcomes.shouldContain(result.task(":compileTestKotlinJvm")?.outcome)
        successOutcomes.shouldContain(result.task(":jvmTest")?.outcome)
    }
})
