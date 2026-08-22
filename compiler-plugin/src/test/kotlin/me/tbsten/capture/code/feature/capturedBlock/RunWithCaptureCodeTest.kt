package me.tbsten.capture.code.feature.capturedBlock

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * `runWithCaptureCode(Marker::class) { ... }` の挙動を kctfork で end-to-end 検証する。
 *
 * expression annotation (`@Marker() (expr)`) との違い:
 * - marker 側に `@Target(AnnotationTarget.EXPRESSION)` が不要
 * - K2 parser の `@Marker()` 空カッコ制約 (design §13.1) を受けない
 * - capture されるのは **lambda の body のみ** (`runWithCaptureCode(...) {` と `}` は含まない)
 */
class RunWithCaptureCodeTest : FunSpec({

    beforeEach {
        CaptureCodeMarkerRegistry.reset()
        CaptureCodeExpressionSiteRegistry.reset()
    }

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    fun loadCaptured(result: JvmCompilationResult, mainFqn: String): List<*> {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    fun sourceValueOf(marker: Any?): String {
        val ann = marker as Annotation
        val filler = ann.annotationClass.java.getMethod("source").invoke(ann) as Annotation
        return filler.annotationClass.java.getMethod("value").invoke(filler) as String
    }

    // ----------------------------------------------------------------
    // 1. lambda body だけが capture される (wrapper 行は含まれない)
    // ----------------------------------------------------------------
    test("runWithCaptureCode captures only the lambda body") {
        val result = compile(
            SourceFile.kotlin(
                "Basic.kt",
                """
                package example.block_basic

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BlockMarker(val source: Source = Source())

                internal fun host() {
                    runWithCaptureCode(BlockMarker::class) {
                        println("hoge")
                        println("fuga")
                    }
                }

                internal object Main {
                    fun captured(): List<BlockMarker> = capturedSources<BlockMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.block_basic.Main")
        captured shouldHaveSize 1
        sourceValueOf(captured[0]) shouldBe "println(\"hoge\")\nprintln(\"fuga\")"
    }

    // ----------------------------------------------------------------
    // 2. 戻り値 (API C 案の肝): 型引数を明示せずに R が推論される
    // ----------------------------------------------------------------
    test("runWithCaptureCode returns the block value with R inferred") {
        val result = compile(
            SourceFile.kotlin(
                "Returning.kt",
                """
                package example.block_return

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ReturnMarker(val source: Source = Source())

                internal object Main {
                    fun captured(): List<ReturnMarker> = capturedSources<ReturnMarker>()

                    fun value(): Int = runWithCaptureCode(ReturnMarker::class) {
                        val a = 1
                        val b = 2
                        a + b
                    }
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("example.block_return.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        mainClass.getMethod("value").invoke(mainInstance) shouldBe 3

        val captured = loadCaptured(result, mainFqn = "example.block_return.Main")
        captured shouldHaveSize 1
        sourceValueOf(captured[0]) shouldBe "val a = 1\nval b = 2\na + b"
    }

    // ----------------------------------------------------------------
    // 3. 同一 file 内の複数 block は source 順に独立して収集される
    // ----------------------------------------------------------------
    test("multiple runWithCaptureCode blocks in one file are collected in source order") {
        val result = compile(
            SourceFile.kotlin(
                "Multi.kt",
                """
                package example.block_multi

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MultiMarker(val source: Source = Source())

                internal fun host() {
                    runWithCaptureCode(MultiMarker::class) {
                        println("first")
                    }
                    runWithCaptureCode(MultiMarker::class) {
                        println("second")
                    }
                }

                internal object Main {
                    fun captured(): List<MultiMarker> = capturedSources<MultiMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.block_multi.Main")
        captured.map { sourceValueOf(it) } shouldBe listOf(
            "println(\"first\")",
            "println(\"second\")",
        )
    }

    // ----------------------------------------------------------------
    // 4. task-149 と同じ死角の negative test:
    //    同名 file (別ディレクトリ) に block site が leak しないこと
    // ----------------------------------------------------------------
    test("block site does not leak into a same-named file in another directory") {
        val result = compile(
            SourceFile.kotlin(
                "featureA/Shared.kt",
                """
                package example.block_dup.a

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DupBlockMarker(val source: Source = Source())

                internal fun host() {
                    runWithCaptureCode(DupBlockMarker::class) {
                        println("only this")
                    }
                }

                internal object Main {
                    fun captured(): List<DupBlockMarker> = capturedSources<DupBlockMarker>()
                }
                """.trimIndent(),
            ),
            // 同じ leaf 名 `Shared.kt` を持つ別ディレクトリの、 十分に長い無関係な file。
            SourceFile.kotlin(
                "featureB/Shared.kt",
                """
                package example.block_dup.b

                class UnrelatedAlpha(val alpha: String, val beta: Int, val gamma: Boolean)

                class UnrelatedBravo(val delta: String, val epsilon: Int, val zeta: Boolean)

                class UnrelatedCharlie(val eta: String, val theta: Int, val iota: Boolean)

                fun unrelated(): String = "never captured"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.block_dup.a.Main")
        captured.map { sourceValueOf(it) } shouldBe listOf("println(\"only this\")")
    }

    // ----------------------------------------------------------------
    // 5. @CaptureCode の付いていない annotation class を渡すと compile error (bug-008)。
    //    旧挙動は silent に 0 件 capture だったが、 「付けたのに何も起きない」 誤用を
    //    error に格上げした (capturedSources<T>() の T 検査と同じ方針)。
    // ----------------------------------------------------------------
    test("runWithCaptureCode に @CaptureCode の無い annotation class を渡すと error になる") {
        val result = compile(
            SourceFile.kotlin(
                "NonMarker.kt",
                """
                package example.block_nonmarker

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class RealMarker(val source: Source = Source())

                internal annotation class NotAMarker

                internal fun host() {
                    runWithCaptureCode(NotAMarker::class) {
                        println("should not be captured")
                    }
                }

                internal object Main {
                    fun captured(): List<RealMarker> = capturedSources<RealMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must be annotated with @CaptureCode"
        result.messages shouldContain "example.block_nonmarker.NotAMarker"
    }

    // ----------------------------------------------------------------
    // 6. @CaptureCode meta のある marker なら bug-008 の検査を pass して従来通り capture される
    //    (regression 確認は test 1-4 の既存 OK ケースも兼ねる)
    // ----------------------------------------------------------------
    test("runWithCaptureCode に @CaptureCode 付き marker を渡した場合は error にならず capture される") {
        val result = compile(
            SourceFile.kotlin(
                "MarkerOk.kt",
                """
                package example.block_marker_ok

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources
                import me.tbsten.capture.code.runWithCaptureCode

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class OkMarker(val source: Source = Source())

                internal fun host() {
                    runWithCaptureCode(OkMarker::class) {
                        println("captured")
                    }
                }

                internal object Main {
                    fun captured(): List<OkMarker> = capturedSources<OkMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        loadCaptured(result, mainFqn = "example.block_marker_ok.Main") shouldHaveSize 1
    }
})
