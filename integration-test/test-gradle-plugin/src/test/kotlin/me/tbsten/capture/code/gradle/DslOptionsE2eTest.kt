package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlin.io.path.createTempDirectory

/**
 * dedent DSL option の真の E2E 検証。
 *
 *   - 「dedent=true (デフォルト) ではインデントが除去される」
 *   - 「dedent=false ではインデントが保持される」
 *
 * の 2 ケースを、 真の E2E パス (`plugins { id("me.tbsten.capture.code") } captureCode { dedent = ... }`)
 * で確認する。 fixture `dedent-sample` を 1 つ用意し、 test 実行時に build.gradle.kts の
 * dedent 値だけを書き換えて 2 パターンの compile を走らせる。
 *
 * `:integration-test:test-jvm` 側 (`kotlinCompilerPluginClasspath(project(":compiler-plugin"))`
 * で plugin を直接 attach する経路) では `:gradle-plugin` の `CaptureCodeExtension` を
 * 経由しないため、 DSL option が compiler に届かない設計。 ここで構築した TestKit fixture
 * を使うことで、 ユーザ実利用形態の path 全体を verify する。
 *
 * ## 速度
 * TestKit が起動する子 Gradle build は 1 件あたり 20-90 秒。 dedent option ごとに 1 build
 * 走らせるので合計 2 build。
 */
class DslOptionsE2eTest : StringSpec({

    val fixturesDir: File = run {
        val sysProp = System.getProperty("test-gradle-plugin.fixturesDir")
        if (sysProp != null) {
            File(sysProp)
        } else {
            File("src/test/resources/fixtures").absoluteFile
        }
    }
    val dedentSampleSrcDir: File = File(fixturesDir, "dedent-sample")

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

    /**
     * 既存 fixture を temp dir にコピーし、 build.gradle.kts の dedent option を
     * 引数で指定した値に書き換える。
     *
     * 元 fixture の `settings.gradle.kts` は root プロジェクトを `includeBuild("../../../../../../..")`
     * という相対パスで参照しているため、 temp dir に丸ごとコピーすると相対パスが壊れる。
     * コピー時に root project の絶対パスへ置換する。
     */
    fun copyFixtureWithDedent(dedentValue: Boolean): File {
        val temp: Path = createTempDirectory("dedent-sample-")
        val rootProjectAbs = dedentSampleSrcDir
            .resolve("../../../../../../..")
            .canonicalFile
            .absolutePath
        Files.walk(dedentSampleSrcDir.toPath()).use { stream ->
            stream.forEach { srcPath ->
                val rel = dedentSampleSrcDir.toPath().relativize(srcPath).toString()
                // 既存 fixture が gitignore 範囲外で誤って生成物を持っていた場合の防御。
                if (rel.startsWith(".gradle${File.separator}") ||
                    rel == ".gradle" ||
                    rel.startsWith(".kotlin${File.separator}") ||
                    rel == ".kotlin" ||
                    rel.startsWith("build${File.separator}") ||
                    rel == "build"
                ) return@forEach
                val dst = temp.resolve(rel)
                if (Files.isDirectory(srcPath)) {
                    Files.createDirectories(dst)
                } else {
                    Files.createDirectories(dst.parent)
                    when (rel) {
                        "settings.gradle.kts" -> {
                            val original = String(Files.readAllBytes(srcPath))
                            val rewritten = original.replace(
                                "../../../../../../..",
                                rootProjectAbs,
                            )
                            Files.writeString(dst, rewritten)
                        }
                        "build.gradle.kts" -> {
                            val original = String(Files.readAllBytes(srcPath))
                            // baseline は `dedent = true` なので、 false 指定時のみ置換する。
                            // true 指定時はそのまま (baseline と同値)。
                            val rewritten = if (dedentValue) {
                                original
                            } else {
                                original.replace(
                                    "dedent = true",
                                    "dedent = false",
                                )
                            }
                            Files.writeString(dst, rewritten)
                        }
                        else -> Files.copy(srcPath, dst, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
        return temp.toFile()
    }

    /** 出力中の `TEST_RESULT_BEGIN` ... `TEST_RESULT_END` ブロックから Source.value を Base64 decode して返す。 */
    fun extractCapturedSources(output: String): List<String> {
        val begin = output.indexOf("TEST_RESULT_BEGIN")
        val end = output.indexOf("TEST_RESULT_END")
        check(begin >= 0 && end > begin) {
            "TEST_RESULT 区切りが出力に見つからない: $output"
        }
        val block = output.substring(begin, end + "TEST_RESULT_END".length)
        return block.lineSequence()
            .mapNotNull { line ->
                val prefix = "source_b64="
                if (line.startsWith(prefix)) {
                    String(Base64.getDecoder().decode(line.removePrefix(prefix)), Charsets.UTF_8)
                } else {
                    null
                }
            }
            .toList()
    }

    "dedent=true (デフォルト) ではインデントが除去される" {
        val projectDir = copyFixtureWithDedent(dedentValue = true)
        val result = newRunner(projectDir, ":run").build()

        successOutcomes.contains(result.task(":run")?.outcome) shouldBe true

        val sources = extractCapturedSources(result.output)
        sources.size shouldBe 1
        // dedent=true: 元のインデント (4 spaces) が除去されるので、 関数宣言行が
        // 0 column から始まる。 内側の `return` 行は元の 8 spaces のうち共通の
        // 4 spaces が除去されて 4 spaces だけ残る。
        sources[0] shouldBe "fun indentedMember(): String {\n    return \"hello\"\n}"
    }

    "dedent=false ではインデントが保持される" {
        val projectDir = copyFixtureWithDedent(dedentValue = false)
        val result = newRunner(projectDir, ":run").build()

        successOutcomes.contains(result.task(":run")?.outcome) shouldBe true

        val sources = extractCapturedSources(result.output)
        sources.size shouldBe 1
        // dedent=false: 元のインデント (4 spaces) がそのまま残る。
        sources[0] shouldBe "    fun indentedMember(): String {\n        return \"hello\"\n    }"
    }
})
