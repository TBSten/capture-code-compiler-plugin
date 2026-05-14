package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempDirectory

/**
 * Gradle incremental compile (IC) ON 状態で CaptureCode plugin が
 * 想定通り動くかの実機検証 (R5 緩和)。
 *
 * ## 検証シナリオ
 *   A. `@Snippet` 付き宣言を **追加** → caller 側 (`capturedSources<Snippet>()`) の
 *      出力に反映されること
 *   B. `@Snippet` 付き宣言を **削除** → caller 側出力から消えること
 *   C. `@Snippet` 付き宣言の **本文を編集 (source range の文字列のみ変化)** →
 *      caller 側出力が新しいソースに更新されること
 *
 * ## 検証手法
 *   1. `jvm-sample` fixture を JUnit/Kotest 経由の一時ディレクトリにコピー
 *      (gitignore された `.gradle`/`.kotlin`/`build/` を除外)
 *   2. 1 回目 `./gradlew :run` で baseline 出力をキャプチャ
 *   3. `Usage.kt` を編集 (追加 / 削除 / 編集)
 *   4. **`Main.kt` には触らない** ことを保証した上で再度 `./gradlew :run`
 *   5. 2 回目の出力が編集を反映していることを assert
 *
 * ## IC 設定
 * - Kotlin JVM の IC は default で ON (`kotlin.incremental=true`)。
 * - `kotlin.incremental.useClasspathSnapshot=true` も Kotlin 1.8.20+ で default ON。
 * - 念のため `-Pkotlin.incremental=true` を明示。
 * - configuration-cache は `--no-configuration-cache` で OFF
 *   (includeBuild + substitution との衝突回避; 既存 E2E と同じ方針)。
 *
 * ## R5 fallback (task-069): marker hash を KotlinCompile task input に attach
 *
 * task-033 の baseline 実測で R5 (caller 側が IC に拾われない) が顕在化していた。
 * task-069 で `:gradle-plugin` に `MarkerSetHasher` を導入し、 各 `KotlinCompile`
 * task の `inputs.property` に「marker 集合 (= `@CaptureCode` meta-annotated class
 * とその use sites) の content hash」 を attach することで、 marker 集合に変化が
 * あれば task は強制的に non-up-to-date 扱いになり、 caller を含む module 全体が
 * 再 compile される。 本テストの 3 シナリオはこの fallback の効果を確認する。
 *
 * ## 速度
 * TestKit は 1 build あたり数十秒。3 シナリオ × 2 build = 約 2-5 分を想定。
 */
class IncrementalCompileTest : StringSpec({

    val fixturesDir: File = run {
        val sysProp = System.getProperty("test-gradle-plugin.fixturesDir")
        if (sysProp != null) {
            File(sysProp)
        } else {
            File("src/test/resources/fixtures").absoluteFile
        }
    }
    val jvmSampleSrcDir: File = File(fixturesDir, "jvm-sample")

    val kotlinVersion: String = System.getProperty("test-gradle-plugin.kotlinVersion")
        ?: error("test-gradle-plugin.kotlinVersion system property is not set")

    fun newRunner(projectDir: File, vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(
            *args,
            "-Ptest-gradle-plugin.kotlinVersion=$kotlinVersion",
            "-Pkotlin.incremental=true",
            "-Pkotlin.incremental.useClasspathSnapshot=true",
            "--stacktrace",
            "--no-configuration-cache",
        )
        .forwardOutput()

    val successOutcomes = setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)

    /** 出力中の `TEST_RESULT_BEGIN` ... `TEST_RESULT_END` ブロックを抽出。 */
    fun extractCapturedBlock(output: String): String {
        val begin = output.indexOf("TEST_RESULT_BEGIN")
        val end = output.indexOf("TEST_RESULT_END")
        check(begin >= 0 && end > begin) {
            "TEST_RESULT 区切りが出力に見つからない: $output"
        }
        return output.substring(begin, end + "TEST_RESULT_END".length)
    }

    /** Usage.kt 1 ファイルを書き換えるユーティリティ。 */
    fun writeUsage(projectDir: File, content: String) {
        val usage = File(projectDir, "src/main/kotlin/me/tbsten/capture/code/sample/Usage.kt")
        usage.writeText(content)
    }

    /** Main.kt の最終更新時刻 (IC で「caller 側ファイルが触られていない」ことの保証用)。 */
    fun mainLastModified(projectDir: File): Long {
        val main = File(projectDir, "src/main/kotlin/me/tbsten/capture/code/sample/Main.kt")
        return main.lastModified()
    }

    /**
     * 既存 fixture を temp dir にコピーし、 IC 検証用の独立 workspace を作る。
     *
     * 元 fixture の `settings.gradle.kts` は root プロジェクトを
     * `includeBuild("../../../../../../..")` という相対パスで参照しているため、
     * temp dir に丸ごとコピーすると相対パスが壊れる。 コピー時に root project
     * の絶対パスへ置換する。
     */
    fun copyFixture(): File {
        val temp: Path = createTempDirectory("ic-jvm-sample-")
        // 元 fixture からの 7 階層遡り (`../../../../../../..`) が指す root プロジェクト
        // の絶対パスを事前に解決しておき、 settings の相対パスをこれに置換する。
        val rootProjectAbs = jvmSampleSrcDir
            .resolve("../../../../../../..")
            .canonicalFile
            .absolutePath
        Files.walk(jvmSampleSrcDir.toPath()).use { stream ->
            stream.forEach { srcPath ->
                val rel = jvmSampleSrcDir.toPath().relativize(srcPath).toString()
                // 既存 fixture が gitignore 範囲外で誤って生成物を持っていた場合の
                // 防御。 これらは IC の初期 state を汚すので除外する。
                //
                // 注意: `"build/"` で前方一致を判定する必要がある。 `startsWith("build")`
                // だと `build.gradle.kts` も誤マッチしてしまい、 build script ごと
                // コピーから漏れて fixture が起動できなくなる (`:run` not found 等)。
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
                    if (rel == "settings.gradle.kts") {
                        // 相対 includeBuild path を絶対パスに置換した上で書き込む。
                        val original = String(Files.readAllBytes(srcPath))
                        val rewritten = original.replace(
                            "../../../../../../..",
                            rootProjectAbs,
                        )
                        Files.writeString(dst, rewritten)
                    } else {
                        Files.copy(srcPath, dst, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
        return temp.toFile()
    }

    // ------------------------------------------------------------------------
    // baseline Usage.kt content (= 既存 fixture と同じ shape)
    // ------------------------------------------------------------------------
    val baselineUsage = """
        package me.tbsten.capture.code.sample

        @Snippet
        internal fun greet(): String = "Hello!"

        @Snippet
        internal fun farewell(): String = "Goodbye!"
    """.trimIndent() + "\n"

    // ------------------------------------------------------------------------
    // task-069 fallback 実装後: 3 シナリオすべて pass する想定。
    // baseline (R5 現状確定) のピン留め test は fallback 実装で意味を失ったため削除。
    // ------------------------------------------------------------------------

    "シナリオ A: @Snippet 付き宣言を追加 → caller の capturedSources に反映される" {
        val projectDir = copyFixture()
        writeUsage(projectDir, baselineUsage)

        // 1 回目 build: baseline
        val firstResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(firstResult.task(":run")?.outcome) shouldBe true
        val firstBlock = extractCapturedBlock(firstResult.output)
        firstBlock shouldContain "fun greet()"
        firstBlock shouldContain "\"Hello!\""
        firstBlock shouldContain "fun farewell()"
        firstBlock shouldContain "\"Goodbye!\""
        firstBlock shouldNotContain "fun welcome()"

        val mainMtimeBefore = mainLastModified(projectDir)

        // Usage.kt に新規 @Snippet 関数 `welcome()` を追加。 Main.kt は触らない。
        val updatedUsage = baselineUsage + """

            @Snippet
            internal fun welcome(): String = "Welcome!"
        """.trimIndent() + "\n"
        writeUsage(projectDir, updatedUsage)

        // 2 回目 build: IC で再ビルドされ、 capturedSources に welcome が反映されるか
        val secondResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(secondResult.task(":run")?.outcome) shouldBe true
        val secondBlock = extractCapturedBlock(secondResult.output)
        secondBlock shouldContain "fun greet()"
        secondBlock shouldContain "fun farewell()"
        secondBlock shouldContain "fun welcome()"
        secondBlock shouldContain "\"Welcome!\""

        // Main.kt は触っていない (= IC によって caller 側が連動再コンパイルされた) ことの確証。
        mainLastModified(projectDir) shouldBe mainMtimeBefore
        // 出力 block 自体は 1 回目と異なるはず (welcome 行追加分)
        secondBlock shouldNotBe firstBlock
    }

    "シナリオ B: @Snippet 付き宣言を削除 → caller の capturedSources から消える" {
        val projectDir = copyFixture()
        // baseline は 3 件 (greet / farewell / welcome) からスタート。
        val withWelcomeUsage = baselineUsage + """

            @Snippet
            internal fun welcome(): String = "Welcome!"
        """.trimIndent() + "\n"
        writeUsage(projectDir, withWelcomeUsage)

        val firstResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(firstResult.task(":run")?.outcome) shouldBe true
        val firstBlock = extractCapturedBlock(firstResult.output)
        firstBlock shouldContain "fun welcome()"
        firstBlock shouldContain "\"Welcome!\""

        val mainMtimeBefore = mainLastModified(projectDir)

        // welcome() を削除 (baseline に戻す)
        writeUsage(projectDir, baselineUsage)

        val secondResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(secondResult.task(":run")?.outcome) shouldBe true
        val secondBlock = extractCapturedBlock(secondResult.output)
        secondBlock shouldContain "fun greet()"
        secondBlock shouldContain "fun farewell()"
        secondBlock shouldNotContain "fun welcome()"
        secondBlock shouldNotContain "\"Welcome!\""

        mainLastModified(projectDir) shouldBe mainMtimeBefore
        secondBlock shouldNotBe firstBlock
    }

    "シナリオ C: @Snippet 付き宣言の本文を編集 → caller の capturedSources が新ソースに更新される" {
        val projectDir = copyFixture()
        writeUsage(projectDir, baselineUsage)

        val firstResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(firstResult.task(":run")?.outcome) shouldBe true
        val firstBlock = extractCapturedBlock(firstResult.output)
        firstBlock shouldContain "\"Hello!\""
        firstBlock shouldNotContain "\"Konnichiwa!\""

        val mainMtimeBefore = mainLastModified(projectDir)

        // greet() の本文文字列を書き換え。 関数シグネチャは変えず、 source range の中身だけ変える。
        val editedUsage = """
            package me.tbsten.capture.code.sample

            @Snippet
            internal fun greet(): String = "Konnichiwa!"

            @Snippet
            internal fun farewell(): String = "Goodbye!"
        """.trimIndent() + "\n"
        writeUsage(projectDir, editedUsage)

        val secondResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(secondResult.task(":run")?.outcome) shouldBe true
        val secondBlock = extractCapturedBlock(secondResult.output)
        secondBlock shouldContain "fun greet()"
        secondBlock shouldContain "\"Konnichiwa!\""
        secondBlock shouldNotContain "\"Hello!\""
        secondBlock shouldContain "fun farewell()"
        secondBlock shouldContain "\"Goodbye!\""

        mainLastModified(projectDir) shouldBe mainMtimeBefore
        secondBlock shouldNotBe firstBlock
    }
})
