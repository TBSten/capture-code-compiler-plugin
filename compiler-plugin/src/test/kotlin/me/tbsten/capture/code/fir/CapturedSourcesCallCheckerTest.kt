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
 * Logic G (`capturedSources<T>()` の FIR checker) の unit test。
 *
 * task-011 で追加した
 * [me.tbsten.capture.code.feature.capturedsources.checker.CapturedSourcesCallChecker] が、
 * `T` が `@CaptureCode` メタ付き marker かどうかに応じて適切に compile error を出すか検証する。
 *
 * - **違反系**: `T` が `@CaptureCode` を付けていない一般 annotation → error
 * - **正常系**: `T` が `@CaptureCode` 付き marker → error なし
 * - **境界**: `T` が `kotlin.Annotation` (interface) そのもの → error
 *   (interface なのでそもそも `T : Annotation` の制約をギリ満たすが、instantiation できないので
 *    Kotlin compiler 自体が別 error を出す可能性がある。本 checker は registry にない以上 error を出す)
 *
 * 違反 error メッセージは
 * [me.tbsten.capture.code.error.CapturedSourcesCheckerDiagnostics.CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER]
 * の renderer 文字列 (`Type parameter T of capturedSources<T>() must be annotated with @CaptureCode.`) を
 * 部分一致で検証する。完全一致だと renderer の文言変更で test が割れるため。
 */
class CapturedSourcesCallCheckerTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    // ----------------------------------------------------------------
    // 違反パターン: `@CaptureCode` を付けていない一般 annotation を T として渡すと error
    // ----------------------------------------------------------------
    test("T without @CaptureCode meta-annotation produces COMPILATION_ERROR") {
        val result = compile(
            SourceFile.kotlin(
                "NotAMarker.kt",
                """
                package example

                import me.tbsten.capture.code.capturedSources

                // `@CaptureCode` を付けていないので Capture Code marker ではない
                @Retention(AnnotationRetention.SOURCE)
                annotation class NotAMarker

                object Main {
                    fun captured(): List<NotAMarker> = capturedSources<NotAMarker>()
                }
                """.trimIndent(),
            ),
        )
        // checker が error を出すと exitCode が OK にならない
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        // メッセージに本 checker の error 文言が含まれることを確認
        result.messages shouldContain "must be annotated with @CaptureCode"
        result.messages shouldContain "example.NotAMarker"
    }

    // ----------------------------------------------------------------
    // 正常系: `@CaptureCode` 付き marker を T として渡すと error なし
    // (Logic F の他制約 (internal / @Target / @Retention SOURCE) もすべて満たすことに注意)
    // ----------------------------------------------------------------
    test("T with @CaptureCode meta-annotation compiles without error") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippets(val source: Source = Source())

                @Snippets
                internal val captured = "value"

                internal object Main {
                    fun captured(): List<Snippets> = capturedSources<Snippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        // 本 checker の error 文言が混ざっていないことを確認 (false positive 防止)
        result.messages shouldNotContain "must be annotated with @CaptureCode"
    }

    // ----------------------------------------------------------------
    // 境界: `T` が `kotlin.Annotation` 自体だと違反として扱う
    //
    // ※ `Annotation` interface そのものを capturedSources の T に渡すケース。
    //    `T : Annotation` の bound はギリ満たすが、Capture Code としては意味のある書き換えができない。
    //    `Annotation` 型は registry に登録されない (= @CaptureCode 付きでない) ため、本 checker が
    //    通常通り error を出す。Kotlin compiler 自体が「Annotation interface は instantiation 不可」
    //    のような追加 error を出すかもしれないが、本 test ではあくまで「本 checker の error が出る」
    //    ことだけを確認する。
    // ----------------------------------------------------------------
    test("T as kotlin.Annotation itself produces COMPILATION_ERROR") {
        val result = compile(
            SourceFile.kotlin(
                "AnnotationItself.kt",
                """
                package example

                import me.tbsten.capture.code.capturedSources

                object Main {
                    fun captured(): List<Annotation> = capturedSources<Annotation>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        // 本 checker の error が出ていることを確認 (他 error も混ざっていてよい)
        result.messages shouldContain "must be annotated with @CaptureCode"
    }
})
