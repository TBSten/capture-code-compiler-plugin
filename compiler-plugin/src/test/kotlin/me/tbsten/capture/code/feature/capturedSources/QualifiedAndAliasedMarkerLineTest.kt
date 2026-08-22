package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.markerImportAliases
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * bug-005 regression test: marker を **FQN 記法** (`@example.Snippet`) や **import alias**
 * (`import example.Snippet as Snip` + `@Snip`) で書いた場合でも、 marker 行が capture に
 * leak しないことを検証する。
 *
 * 修正前は `skipLeadingMarkerAnnotations` が `@` の直後の identifier 1 個だけを simple name と
 * して読んでいたため、
 *
 * - `@example.Snippet` → `example` を読む → marker simple name と不一致 → 非 marker 扱い
 * - `@Snip` (alias) → `Snip` は simple name 集合に無い → 非 marker 扱い
 *
 * となり、 marker 行がそのまま source に残っていた (IR 側は annotation 型で判定するため
 * capture 自体は成立し、 壊れるのは文字列だけの silent bug)。
 *
 * compile helper は [AllDeclarationTargetsTest] のパターンを踏襲。
 */
class QualifiedAndAliasedMarkerLineTest : FunSpec({

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
    // unit-level: pure helper の qualified name 読みと alias 解析を直接 verify
    // ----------------------------------------------------------------
    test("skipLeadingMarkerAnnotations は FQN 記法の marker 行を末尾 segment で照合して skip する") {
        val site = CollectDeclarationSite()
        val text = buildString {
            append("@example.Marker\n")
            append("internal fun foo(): Int = 1")
        }
        val result = site.skipLeadingMarkerAnnotations(
            text = text,
            startOffset = 0,
            endOffset = text.length,
            markerSimpleNames = setOf("Marker"),
        )
        // `@example.Marker\n` (= 16 chars) を skip し sourceStart は宣言本体行頭
        result.sourceStart shouldBe 16
        result.markerRanges shouldBe emptyList()
    }

    test("markerImportAliases は marker FqN への alias だけを返す") {
        CaptureCodeMarkerRegistry.reset()
        try {
            CaptureCodeMarkerRegistry.registerMarker("example.Snippet")
            val fileText = buildString {
                append("package example\n")
                append("\n")
                append("import example.Snippet as Snip\n")
                append("import example.NotAMarker as Other\n")
                append("import me.tbsten.capture.code.CaptureCode\n")
                append("\n")
                append("internal fun decl() = 1\n")
                // 宣言開始後の偽 import 行 (multi-line string 内などを想定) は無視される
                append("val s = \"import example.Snippet as Fake\"\n")
            }
            markerImportAliases(fileText) shouldBe setOf("Snip")
        } finally {
            CaptureCodeMarkerRegistry.reset()
        }
    }

    // ----------------------------------------------------------------
    // e2e: FQN 記法 (`@example.Snippet`)
    // ----------------------------------------------------------------
    test("FQN 記法で書いた marker 行は capture に残らない") {
        val result = compile(
            SourceFile.kotlin(
                "FqnMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @example.Snippet
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
    // e2e: import alias (`import example.Snippet as Snip` + `@Snip`)
    // ----------------------------------------------------------------
    test("import alias で書いた marker 行は capture に残らない") {
        // marker 宣言と alias 利用側を別 file に分ける (同一 file 内の alias import は
        // K2 で元の simple name の解決を壊すため、 実運用と同じ「別 file の marker を
        // alias import する」形にしている)。
        val result = compile(
            SourceFile.kotlin(
                "AliasedMarkerDecl.kt",
                """
                package example.marker

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                annotation class Snippet(val source: Source = Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "AliasedMarkerUse.kt",
                """
                package example

                import example.marker.Snippet as Snip
                import me.tbsten.capture.code.capturedSources

                @Snip
                internal fun target() = "target body"

                internal object Main {
                    fun captured(): List<Snip> = capturedSources<Snip>()
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
    // e2e: 非 marker annotation への alias は誤って drop されない
    // ----------------------------------------------------------------
    test("非 marker annotation への alias は capture に残る") {
        val result = compile(
            SourceFile.kotlin(
                "NonMarkerAlias.kt",
                """
                package example

                import example.PlainAnnot as Alias
                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @Target(AnnotationTarget.FUNCTION)
                internal annotation class PlainAnnot

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Alias
                @Snippet
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
            "@Alias\ninternal fun target() = \"target body\""
    }
})
