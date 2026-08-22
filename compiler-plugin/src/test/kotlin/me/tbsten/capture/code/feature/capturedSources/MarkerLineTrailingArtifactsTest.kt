package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite

/**
 * bug-010 regression test: capture テキストの取りこぼし 2 件を検証する。
 *
 * 1. **marker 行の trailing comment**: `@Snippet // why` のように marker token の後に
 *    line comment があると、 修正前は marker token + 空白のみ skip され `// why` の残骸が
 *    capture 先頭に leak していた。 修正後は行末までの `// ...` を marker 行の一部として吸収する。
 * 2. **同一行の次宣言のセミコロン**: `@Snippet internal val a = 1; internal val b = 2` の
 *    1 番目の declaration は IR endOffset が `;` を含むため capture が `internal val a = 1;`
 *    になっていた。 修正後は normalize 前に rawBody 末尾の `;` を 1 つだけ strip する
 *    (文中の `;` は触らない)。
 *
 * issue 05 の (3) use-site target / (4) enum entry は別対応のため本テストの対象外。
 * compile helper は [AllDeclarationTargetsTest] のパターンを踏襲。
 */
class MarkerLineTrailingArtifactsTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    fun captureSourceValue(annotation: Annotation): String {
        val sourceMethod = annotation.annotationClass.java.getMethod("source")
        val sourceAnnotation = sourceMethod.invoke(annotation) as Annotation
        val valueMethod = sourceAnnotation.annotationClass.java.getMethod("value")
        return valueMethod.invoke(sourceAnnotation) as String
    }

    fun loadCaptured(result: JvmCompilationResult): List<*> {
        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    // ----------------------------------------------------------------
    // unit-level: trailing comment 吸収の offset 計算を直接 verify
    // ----------------------------------------------------------------
    test("skipLeadingMarkerAnnotations は marker 行末の line comment ごと行全体を skip する") {
        val site = CollectDeclarationSite()
        val text = buildString {
            append("@Marker // why\n")
            append("internal fun foo(): Int = 1")
        }
        val result = site.skipLeadingMarkerAnnotations(
            text = text,
            startOffset = 0,
            endOffset = text.length,
            markerSimpleNames = setOf("Marker"),
        )
        // `@Marker // why\n` (= 15 chars) を丸ごと skip し sourceStart は宣言本体行頭
        result.sourceStart shouldBe 15
        result.markerRanges shouldBe emptyList()
    }

    // ----------------------------------------------------------------
    // e2e: marker 行の trailing comment がコメント残骸として leak しない
    // ----------------------------------------------------------------
    test("marker 行の trailing comment は capture に残らない") {
        val result = compile(
            SourceFile.kotlin(
                "TrailingComment.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Snippet // why this is captured
                internal fun target() = "target body"

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "internal fun target() = \"target body\""
    }

    // ----------------------------------------------------------------
    // e2e: 同一行の次宣言との区切り `;` が capture 末尾に残らない
    // ----------------------------------------------------------------
    test("同一行に次の宣言が続く場合でも capture 末尾にセミコロンが残らない") {
        val result = compile(
            SourceFile.kotlin(
                "TrailingSemicolon.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Snippet internal val a = 1; internal val b = 2

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "internal val a = 1"
    }

    // ----------------------------------------------------------------
    // e2e: 文中の `;` と `}` 終わりの通常宣言には影響しない
    // ----------------------------------------------------------------
    test("文中のセミコロンと block body 終端の閉じ括弧はそのまま保持される") {
        val result = compile(
            SourceFile.kotlin(
                "MidSemicolon.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Snippet
                internal fun multi(): Int {
                    val a = 1; val b = 2
                    return a + b
                }

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "internal fun multi(): Int {\n    val a = 1; val b = 2\n    return a + b\n}"
    }
})
