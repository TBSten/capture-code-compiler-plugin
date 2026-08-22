package me.tbsten.capture.code.fir

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * Logic G (`ValidateCapturedSourcesCall`) が **user のコンパイルエラーを潰さない** ことの
 * regression test。
 *
 * FIR checker は resolution error を含む file に対しても走る。 このとき function call の
 * `calleeReference` は `FirErrorNamedReference` になるが、 checker が `require(...)` で
 * fail-fast すると `IllegalArgumentException` が FIR 解析中に投げられ、 compiler が
 * `COMPILATION_ERROR` (= 通常の "unresolved reference") ではなく `INTERNAL_ERROR`
 * (= compiler crash) で終了してしまう。
 *
 * plugin を適用しただけで **CaptureCode API を一切使っていない file の typo** まで
 * compiler crash に化けるため、 影響範囲は plugin 利用者のコンパイル体験全体に及ぶ。
 */
class UnresolvedCalleeCrashTest : FunSpec({

    fun compile(vararg sources: SourceFile): Pair<JvmCompilationResult, String> {
        val output = java.io.ByteArrayOutputStream()
        val result = KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = output
        }.compile()
        return result to output.toString()
    }

    // ----------------------------------------------------------------
    // CaptureCode API を一切使わない file の typo は、 素の
    // "unresolved reference" として報告されなければならない。
    // ----------------------------------------------------------------
    test("an unresolved call in a file that never touches CaptureCode reports unresolved reference") {
        val (result, output) = compile(
            SourceFile.kotlin(
                "Typo.kt",
                """
                package example.typo

                fun host() {
                    thisFunctionDoesNotExist(1, 2)
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        output shouldContain "thisFunctionDoesNotExist"
        output shouldNotContain "ValidateCapturedSourcesCall"
    }

    // ----------------------------------------------------------------
    // marker / capturedSources を使っている file に typo が混ざった場合も同様。
    // ----------------------------------------------------------------
    test("an unresolved call next to a capturedSources call reports unresolved reference") {
        val (result, output) = compile(
            SourceFile.kotlin(
                "TypoWithMarker.kt",
                """
                package example.typo_marker

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class TypoMarker(val source: Source = Source())

                @TypoMarker
                internal fun marked() = 1

                internal fun host() {
                    alsoDoesNotExist()
                }

                internal object Main {
                    fun captured(): List<TypoMarker> = capturedSources<TypoMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        output shouldContain "alsoDoesNotExist"
        output shouldNotContain "ValidateCapturedSourcesCall"
    }

    // ----------------------------------------------------------------
    // 型引数を明示せず期待型から推論させる呼び出し
    // (`val x: List<Marker> = capturedSources()`) でも crash しないこと。
    // ----------------------------------------------------------------
    test("capturedSources() with an inferred type argument does not crash the compiler") {
        val (result, output) = compile(
            SourceFile.kotlin(
                "Inferred.kt",
                """
                package example.inferred

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class InferredMarker(val source: Source = Source())

                @InferredMarker
                internal fun marked() = 1

                internal object Main {
                    fun captured(): List<InferredMarker> = capturedSources()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        output shouldNotContain "ValidateCapturedSourcesCall"
    }
})
