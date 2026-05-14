package me.tbsten.capture.code.fir

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * Logic F — `MarkerAnnotationChecker` の unit test。
 *
 * `compiler-plugin-design.md` §5 Logic F に列挙された 6 種類の制約違反パターンそれぞれを
 * negative test として確認する。さらに正常系 (規約通りの marker) で error が出ないことも
 * 確認する。
 *
 * テストは kctfork (`KotlinCompilation`) を直接駆動して plugin 適用下の compile を回し、
 * `JvmCompilationResult.exitCode` と `messages` の英語文字列で検証する。
 *
 * Diagnostic ID は `CaptureCodeDiagnostics` の `KtDiagnosticFactory0` / `KtDiagnosticFactory1`
 * の property name (例: `MARKER_NOT_INTERNAL_OR_PRIVATE`) として `messages` に含まれる。
 */
class MarkerAnnotationCheckerTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    // ----------------------------------------------------------------
    // (1) public marker → error
    // ----------------------------------------------------------------
    test("public marker annotation reports MARKER_NOT_INTERNAL_OR_PRIVATE") {
        val result = compile(
            SourceFile.kotlin(
                "PublicMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                annotation class PublicMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must be 'internal' or 'private'"
    }

    // ----------------------------------------------------------------
    // (2) @Retention(AnnotationRetention.BINARY) marker → error
    // ----------------------------------------------------------------
    test("BINARY retention marker reports MARKER_RETENTION_NOT_SOURCE") {
        val result = compile(
            SourceFile.kotlin(
                "BinaryRetention.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.BINARY)
                internal annotation class BinaryRetentionMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "@Retention(AnnotationRetention.SOURCE)"
    }

    // ----------------------------------------------------------------
    // (2b) @Retention 未指定 (default RUNTIME) でも error になることを確認
    // ----------------------------------------------------------------
    test("missing @Retention defaults to RUNTIME and reports MARKER_RETENTION_NOT_SOURCE") {
        val result = compile(
            SourceFile.kotlin(
                "DefaultRetention.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                internal annotation class DefaultRetentionMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "@Retention(AnnotationRetention.SOURCE)"
    }

    // ----------------------------------------------------------------
    // (3) @Target() (空) marker → error
    // ----------------------------------------------------------------
    test("empty @Target reports MARKER_TARGET_EMPTY") {
        val result = compile(
            SourceFile.kotlin(
                "EmptyTarget.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target()
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class EmptyTargetMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "@Target site"
    }

    // ----------------------------------------------------------------
    // (3b) @Target 未指定 (annotation 自体が無い) でも error になることを確認
    // ----------------------------------------------------------------
    test("missing @Target reports MARKER_TARGET_EMPTY") {
        val result = compile(
            SourceFile.kotlin(
                "MissingTarget.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MissingTargetMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "@Target site"
    }

    // ----------------------------------------------------------------
    // (4) parameter 型が annotation 制約外 → error
    // ----------------------------------------------------------------
    test("non-annotation-class parameter type reports MARKER_PARAMETER_TYPE_INVALID") {
        val result = compile(
            SourceFile.kotlin(
                "InvalidParamType.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                // 通常の class (annotation でも enum でもない)
                class SomeClass(val v: Int)

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class InvalidParamMarker(
                    val item: SomeClass,
                    val source: Source = Source(),
                )
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "has an unsupported type"
        // パラメータ名がメッセージに含まれる
        result.messages shouldContain "'item'"
    }

    // ----------------------------------------------------------------
    // (5) filler 型 parameter にデフォルト値なし → error
    // ----------------------------------------------------------------
    test("filler parameter without default value reports MARKER_FILLER_REQUIRES_DEFAULT") {
        val result = compile(
            SourceFile.kotlin(
                "FillerNoDefault.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FillerNoDefault(val source: Source)
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must have a default value"
        result.messages shouldContain "'source'"
    }

    // ----------------------------------------------------------------
    // (6) expect annotation class XyzMarker → error
    // ----------------------------------------------------------------
    test("expect annotation class reports MARKER_IS_EXPECT_ANNOTATION") {
        val result = compile(
            SourceFile.kotlin(
                "ExpectMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal expect annotation class ExpectMarker(val source: Source = Source())
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "cannot be declared as 'expect'"
    }

    // ----------------------------------------------------------------
    // 正常系: 設計通りの marker (internal + SOURCE + 1 個以上の target + 適切な parameter) は
    // checker から error が出ない
    // ----------------------------------------------------------------
    test("well-formed marker (internal + SOURCE + target + filler default) compiles without error") {
        val result = compile(
            SourceFile.kotlin(
                "WellFormed.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.CaptureKind

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class WellFormedMarker(
                    val label: String = "untitled",
                    val priority: Int = 0,
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                    val kind: CaptureKind = CaptureKind(),
                )

                // private marker も OK
                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                private annotation class PrivateMarker(val source: Source = Source())

                @WellFormedMarker
                val example = "ok"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        // checker 由来の error message (rendered text) が出ていないことを確認
        val msgs = result.messages
        // CaptureCode plugin の Marker 系 diagnostic は 1 つも出てはいけない
        listOf(
            "must be 'internal' or 'private'",
            "@Retention(AnnotationRetention.SOURCE)",
            "@Target site",
            "has an unsupported type",
            "must have a default value",
            "cannot be declared as 'expect'",
        ).forEach { phrase ->
            check(phrase !in msgs) {
                "Unexpected diagnostic phrase '$phrase' in messages:\n$msgs"
            }
        }
    }
})
