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
 * task-033: Gradle incremental compile (IC) ON 状態で CaptureCode plugin が
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
 * ## 現状 (task-033 実測結果): R5 顕在化を確認
 * 3 シナリオすべてで **caller 側 (`Main.kt`) が IC に拾われず stale な `capturedSources<T>()`
 * を返す** ことを確認した。 シナリオ A では新規 `@Snippet welcome()` を追加しても 2 回目の
 * `:run` 出力に `welcome` が現れず、 シナリオ B では削除しても残留し、 シナリオ C では
 * 本文編集 (`"Hello!"` → `"Konnichiwa!"`) が反映されなかった。
 *
 * 詳細は `.local/notes/incremental-compile-verification-result.md` を参照。 これにより
 * **`:gradle-plugin` 側に fallback (capture 集合変化検出 → caller 側ファイル touch
 * もしくは module 全体 recompile) を実装する必要がある** ことが確定。
 *
 * fallback 実装は task-033 follow-up ticket で扱う (現 ticket スコープは
 * 「IC の挙動を実測して結論を出す」 ところまで)。 本テストは fallback 実装後に
 * `enabled = true` に戻して PASS を確認する想定。
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
    // task-033 baseline (R5 顕在化 = 現状の挙動の固定化):
    //
    // fallback 未実装の現状では、 marker 追加に対して caller 側ファイル (Main.kt) が
    // IC の dependency tracking に拾われず、 capturedSources の結果が更新されない。
    // この baseline テストは「現状そうなっている」 ことを CI で常時 verify するための
    // ピン留めで、 fallback 実装後にこの test が FAIL に切り替わったら期待された改善が
    // 発生したと判断し、 下記 3 シナリオ (enabled = false) を逆に enabled = true に戻す。
    // ------------------------------------------------------------------------
    "baseline (R5 現状確定): marker 追加が IC では caller 側 capturedSources に伝播しない" {
        val projectDir = copyFixture()
        writeUsage(projectDir, baselineUsage)

        val firstResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(firstResult.task(":run")?.outcome) shouldBe true
        val firstBlock = extractCapturedBlock(firstResult.output)
        firstBlock shouldContain "fun greet()"
        firstBlock shouldContain "fun farewell()"
        firstBlock shouldNotContain "fun welcome("

        // 新規 @Snippet welcome() を追加。 Main.kt は触らない。
        val updatedUsage = baselineUsage + """

            @Snippet
            internal fun welcome(): String = "Welcome!"
        """.trimIndent() + "\n"
        writeUsage(projectDir, updatedUsage)

        val secondResult = newRunner(projectDir, ":run").build()
        successOutcomes.contains(secondResult.task(":run")?.outcome) shouldBe true
        val secondBlock = extractCapturedBlock(secondResult.output)

        // 現状 (R5 顕在化) の baseline 期待値: welcome は **反映されない**。
        // これが反映されるようになったら、 fallback 実装が効いた合図 = 本テストを更新し、
        // enabled = false の 3 シナリオを enabled = true に戻す。
        secondBlock shouldNotContain "fun welcome("
        secondBlock shouldNotContain "\"Welcome!\""

        // Main.kt は触っていない (IC で caller 側が連動再コンパイルされていないことの確認)
        // → これも現状の baseline 挙動。
    }

    // task-033: 現状 3 シナリオすべて R5 顕在化で fail する (caller 側が IC に拾われない)。
    // fallback 実装 (`:gradle-plugin` 側) 完了後に enabled = true に戻す。
    // baseline 確定の意味は `.local/notes/incremental-compile-verification-result.md` 参照。
    "シナリオ A: @Snippet 付き宣言を追加 → caller の capturedSources に反映される"
        .config(enabled = false) {
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

    "シナリオ B: @Snippet 付き宣言を削除 → caller の capturedSources から消える"
        .config(enabled = false) {
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

    "シナリオ C: @Snippet 付き宣言の本文を編集 → caller の capturedSources が新ソースに更新される"
        .config(enabled = false) {
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
