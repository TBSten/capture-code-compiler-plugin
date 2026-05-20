package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite

/**
 * BUG-A (`task-129`) regression test: 非 marker annotation が marker annotation の **前** に
 * 並んでいる場合 (= `@Suppress → @Marker → fun ...`) でも、 source に marker literal が leak
 * しないことを verify する。
 *
 * 既存テスト `AllDeclarationTargetsTest #6 non-marker annotations following the marker are
 * preserved` は **逆順** (`@Marker → @Suppress → fun ...`) を verify するもの。 こちらは
 * non-marker → marker の順を verify することで、 順序非依存に marker drop が機能することを担保する。
 *
 * 修正前は `skipLeadingAnnotationLines` が「先頭が非 marker annotation だったら打ち切り」 という
 * 方針だったため、 `@Suppress` 行で走査が中断し、 続く `@Marker` 行が source に残ってしまっていた。
 * 修正後は `skipLeadingMarkerAnnotations` で 1 pass 走査 + range drop に変更し、 順序を問わず
 * marker annotation 行のみを drop する。
 */
class NonFirstMarkerLeakTest : FunSpec({

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
    // unit-level: pure helper `skipLeadingMarkerAnnotations` の挙動を offset 計算込みで verify
    //
    // BUG-A の根本である「先頭走査の中で marker 行 range のみ drop」 ロジックが、
    // 「sourceStart は最初の非 marker 行頭、 markerRanges は marker 行の (lineStart..lineEnd+\n)」
    // を返すことを直接確認する。 e2e の失敗時に切り分けやすくするための保険。
    // ----------------------------------------------------------------
    test("skipLeadingMarkerAnnotations drops marker line between non-marker annotations") {
        val site = CollectDeclarationSite()
        val text = buildString {
            append("@Suppress(\"unused\")\n")
            append("@Marker\n")
            append("internal fun foo(): Int = 1")
        }
        val result = site.skipLeadingMarkerAnnotations(
            text = text,
            startOffset = 0,
            endOffset = text.length,
            markerSimpleNames = setOf("Marker"),
        )
        result.sourceStart shouldBe 0
        result.markerRanges.size shouldBe 1
        // `@Marker\n` 行 = `@Suppress("unused")\n` (= 20 chars) の直後から `\n` まで
        result.markerRanges[0] shouldBe (20 until 28)

        // body 抽出 + range drop の結合結果を確認 (= ExtractSiteSource.extractDeclarationSource と同様の手順)
        val rawBody = text.substring(result.sourceStart, text.length)
        val builder = StringBuilder(rawBody)
        result.markerRanges
            .map { range ->
                val start = range.first - result.sourceStart
                val endExclusive = range.last + 1 - result.sourceStart
                start to endExclusive
            }
            .sortedByDescending { it.first }
            .forEach { (start, endExclusive) -> builder.delete(start, endExclusive) }
        builder.toString() shouldBe "@Suppress(\"unused\")\ninternal fun foo(): Int = 1"
    }

    // ----------------------------------------------------------------
    // unit-level edge case 1: 先頭が marker、 続きが非 marker → 旧 skipLeadingAnnotationLines と同じ挙動
    //
    // 先頭 marker のみ skip し、 続く `@Suppress` 行頭から source が始まる。
    // marker range は記録されない (markerRanges = []) ことを verify する。
    // ----------------------------------------------------------------
    test("skipLeadingMarkerAnnotations skips leading marker only and keeps following non-marker") {
        val site = CollectDeclarationSite()
        val text = buildString {
            append("@Marker\n")
            append("@Suppress(\"unused\")\n")
            append("internal fun foo(): Int = 1")
        }
        val result = site.skipLeadingMarkerAnnotations(
            text = text,
            startOffset = 0,
            endOffset = text.length,
            markerSimpleNames = setOf("Marker"),
        )
        // `@Marker\n` (= 8 chars) を skip し sourceStart は `@Suppress` 行頭
        result.sourceStart shouldBe 8
        result.markerRanges shouldBe emptyList()
    }

    // ----------------------------------------------------------------
    // unit-level edge case 2: 全行が marker のみ
    //
    // sourceStart は declaration 本体行 (= `internal fun foo` 行頭) になる。
    // markerRanges には記録しない (= 旧 skipLeadingAnnotationLines と同様の挙動)。
    // ----------------------------------------------------------------
    test("skipLeadingMarkerAnnotations skips all leading marker lines without recording ranges") {
        val site = CollectDeclarationSite()
        val text = buildString {
            append("@Marker\n")
            append("@AnotherMarker\n")
            append("internal fun foo(): Int = 1")
        }
        val result = site.skipLeadingMarkerAnnotations(
            text = text,
            startOffset = 0,
            endOffset = text.length,
            markerSimpleNames = setOf("Marker", "AnotherMarker"),
        )
        // `@Marker\n` (8 chars) + `@AnotherMarker\n` (15 chars) = 23
        result.sourceStart shouldBe 23
        result.markerRanges shouldBe emptyList()
    }

    // ----------------------------------------------------------------
    // BUG-A 再現ケース: `@Suppress("unused") → @Marker → fun foo`
    //
    // 期待: source = "@Suppress(\"unused\")\ninternal fun foo(): Int = 1"
    // 修正前: source に "@Marker" 行が leak し "@Suppress(\"unused\")\n@FunSnippetsNonFirst\ninternal fun foo(): Int = 1" が出力されていた。
    // ----------------------------------------------------------------
    test("non-marker annotation preceding marker keeps marker literal out of source") {
        val result = compile(
            SourceFile.kotlin(
                "SuppressThenMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FunSnippetsNonFirst(val source: Source = Source())

                @Suppress("unused")
                @FunSnippetsNonFirst
                internal fun foo(): Int = 1

                internal object Main {
                    fun captured(): List<FunSnippetsNonFirst> = capturedSources<FunSnippetsNonFirst>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "@Suppress(\"unused\")\ninternal fun foo(): Int = 1"
    }

    // ----------------------------------------------------------------
    // 3 つの annotation を交互に並べたケース: `@Suppress → @Marker → @Deprecated → fun`
    //
    // marker 行を中間に挟みつつ、 前後の非 marker annotation はすべて source に残す。
    // (= 「先頭が非 marker でも、 中間 marker 行を drop する」 ことを 3 行構成で verify する)
    // ----------------------------------------------------------------
    test("marker between non-marker annotations is dropped while surrounding annotations remain") {
        val result = compile(
            SourceFile.kotlin(
                "MixedAnnotations.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FunSnippetsMixed(val source: Source = Source())

                @Suppress("unused")
                @FunSnippetsMixed
                @Deprecated("legacy")
                internal fun bar(): Int = 2

                internal object Main {
                    fun captured(): List<FunSnippetsMixed> = capturedSources<FunSnippetsMixed>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "@Suppress(\"unused\")\n@Deprecated(\"legacy\")\ninternal fun bar(): Int = 2"
    }
})
