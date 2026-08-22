package me.tbsten.capture.code.feature.capturedExpression

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * bug-006 regression test: インデントされた文脈の `@Marker() run { ... }` (= 行の途中から
 * 始まる式) で capture のインデントが崩れないことを検証する。
 *
 * 修正前は `reattachOwnLeadingIndent` が「式が行頭から始まる」場合にのみインデントを復元
 * するため、 `val value = @Snippet() run {` のような行の途中から始まる式では 1 行目が
 * 見かけ上 0 インデントになり、 dedent の最小幅が 0 と判定されて 2 行目以降が元 file の
 * 絶対インデントのまま残っていた:
 *
 * ```
 * run {
 *         val a = 1
 *     }
 * ```
 *
 * 修正後は式起源の dedent で 1 行目を最小インデント幅の計算から除外する
 * (`NormalizeOptions.dedentIgnoreFirstLine`)。 行頭開始式 (reattach 経路) は 1 行目の
 * 復元インデントが残り行の最小幅と一致するため出力は従来と同一
 * ([ExpressionAnnotationTest] の BUG-J regression ケースが引き続き green であることが保証)。
 */
class ExpressionFirstLineDedentTest : FunSpec({

    beforeEach {
        // 連続 compile で前回の site が残らないよう registry を毎回リセット
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

    fun captureSourceValue(marker: Annotation): String {
        val src = marker.annotationClass.java.getMethod("source").invoke(marker) as Annotation
        return src.annotationClass.java.getMethod("value").invoke(src) as String
    }

    // ----------------------------------------------------------------
    // bug-006 再現形: class 内 property 初期化子の `@Snippet() run { ... }`
    // ----------------------------------------------------------------
    test("class 内 property 初期化子の行の途中から始まる式でもインデントが揃う") {
        val result = compile(
            SourceFile.kotlin(
                "MidLineIndented.kt",
                """
                package example.expr_midline

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                internal class Holder {
                    val value = @Snippet() run {
                        val a = 1
                        val b = 2
                        a + b
                    }
                }

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.expr_midline.Main")
        captured shouldHaveSize 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            """
            run {
                val a = 1
                val b = 2
                a + b
            }
            """.trimIndent()
    }

    // ----------------------------------------------------------------
    // 既存挙動の invariance: top-level (column 0) の行の途中から始まる式
    // ----------------------------------------------------------------
    test("top-level の行の途中から始まる式は従来どおりの出力のまま変わらない") {
        val result = compile(
            SourceFile.kotlin(
                "MidLineTopLevel.kt",
                """
                package example.expr_midtop

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                val topValue = @Snippet() run {
                    val a = 1
                    a
                }

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.expr_midtop.Main")
        captured shouldHaveSize 1
        // top-level は閉じ括弧が column 0 にあるため従来から正しく出ていた形。 1 行目除外
        // dedent でも結果は不変 (2 行目以降の最小幅 = 0)。
        captureSourceValue(captured[0] as Annotation) shouldBe
            """
            run {
                val a = 1
                a
            }
            """.trimIndent()
    }

    // ----------------------------------------------------------------
    // 既存挙動の invariance: 関数 body 内の行頭開始式 (reattachOwnLeadingIndent 経路)
    // ----------------------------------------------------------------
    test("関数 body 内の行頭開始式は 1 行目のインデント復元と合わせて従来どおり揃う") {
        val result = compile(
            SourceFile.kotlin(
                "LineStartIndented.kt",
                """
                package example.expr_linestart

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                internal fun host(): Int {
                    @Snippet()
                    run {
                        val x = 1
                        return x
                    }
                }

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.expr_linestart.Main")
        captured shouldHaveSize 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            """
            run {
                val x = 1
                return x
            }
            """.trimIndent()
    }
})
