package me.tbsten.capture.code.feature.captured_sources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * **File annotation** (`@file:Marker`) の挙動を kctfork で end-to-end 検証する。
 *
 * `IrFile.annotations` 経由で marker を発見し、ファイル全体テキスト (`SourceTextExtractor.loadFileText`)
 * を `SourceNormalizer.normalize(NormalizeOptions.FILE_DEFAULT)` で正規化した結果が `Source(value)`
 * filler に詰まること、`CaptureKind` が `FILE` であること、`SourceLocation` の `packageName` /
 * `startLine` / `endLine` が file 全体を指すことを確認する smoke test 群。
 *
 * ## 非対象 (本ファイル scope 外)
 *
 * - declaration 起源との混在ケース: integration-test の IntegrationCasesTest (file annotation 系) で確認
 * - `includeImports = true` で package / import 行を残す path: Gradle DSL 配線の別経路で確認
 * - 式 annotation: [me.tbsten.capture.code.feature.captured_expression.ExpressionAnnotationTest] で確認
 */
class FileAnnotationTest : FunSpec({

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

    fun fillerAnnotation(marker: Annotation, fillerMethodName: String): Annotation {
        val method = marker.annotationClass.java.getMethod(fillerMethodName)
        return method.invoke(marker) as Annotation
    }

    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    // ----------------------------------------------------------------
    // 1. @file:Marker で source filler が file 全体テキスト (package / import 除く) になる
    // ----------------------------------------------------------------
    test("file annotation: Source filler captures file body without package and import lines") {
        val result = compile(
            SourceFile.kotlin(
                "FileLevel.kt",
                """
                @file:SnippetFile

                package example.file_only

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FILE)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SnippetFile(val source: Source = Source())

                val alpha = 1
                val beta = 2

                internal object Main {
                    fun captured(): List<SnippetFile> = capturedSources<SnippetFile>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.file_only.Main")
        captured shouldHaveSize 1
        val marker = captured[0] as Annotation
        val sourceFiller = fillerAnnotation(marker, "source")
        val sourceText = annotationProperty(sourceFiller, "value") as String
        // package / import 行が除外されていることを確認 (`includeImports = false` がデフォルト)
        sourceText shouldNotContain "package "
        sourceText shouldNotContain "import "
        // 先頭の `@file:` annotation 行も除外されることを確認 (`includeAnnotationLines = false` がデフォルト)
        sourceText shouldNotContain "@file:"
        // marker class 自身の declaration はキャプチャ対象から除外されることを確認
        // (marker class 自身は capture のメタ情報であって site そのものではない)
        sourceText shouldNotContain "annotation class SnippetFile"
        // file 内に定義された宣言は残っている
        sourceText shouldContain "val alpha = 1"
        sourceText shouldContain "val beta = 2"
    }

    // ----------------------------------------------------------------
    // 2. CaptureKind filler が FILE になる
    // ----------------------------------------------------------------
    test("file annotation: CaptureKind filler value is FILE") {
        val result = compile(
            SourceFile.kotlin(
                "FileKind.kt",
                """
                @file:SnippetFileKind

                package example.file_kind

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FILE)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SnippetFileKind(val kind: CaptureKind = CaptureKind())

                val gamma = 10

                internal object Main {
                    fun captured(): List<SnippetFileKind> = capturedSources<SnippetFileKind>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.file_kind.Main")
        captured shouldHaveSize 1
        val marker = captured[0] as Annotation
        val kindFiller = fillerAnnotation(marker, "kind")
        val enumValue = annotationProperty(kindFiller, "value") as Enum<*>
        enumValue.name shouldBe "FILE"
    }

    // ----------------------------------------------------------------
    // 3. SourceLocation filler が packageName + lines を埋める (file 起源は startLine=1)
    // ----------------------------------------------------------------
    test("file annotation: SourceLocation filler reports file-wide line range") {
        val result = compile(
            SourceFile.kotlin(
                "FileLoc.kt",
                """
                @file:SnippetFileLoc

                package example.file_loc

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FILE)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SnippetFileLoc(val location: SourceLocation = SourceLocation())

                val delta = 100

                internal object Main {
                    fun captured(): List<SnippetFileLoc> = capturedSources<SnippetFileLoc>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.file_loc.Main")
        captured shouldHaveSize 1
        val marker = captured[0] as Annotation
        val locationFiller = fillerAnnotation(marker, "location")
        annotationProperty(locationFiller, "packageName") shouldBe "example.file_loc"
        // file 起源では startLine は常に 1
        annotationProperty(locationFiller, "startLine") shouldBe 1
        // endLine は file の総行数。少なくとも 1 より大きい (実 fixture 依存なので緩い assert)
        val endLine = annotationProperty(locationFiller, "endLine") as Int
        (endLine > 1) shouldBe true
    }

    // ----------------------------------------------------------------
    // 4. file annotation + declaration annotation 混在 (同一 marker、同一 module)
    //    → kind で識別できる 2 件のリスト
    // ----------------------------------------------------------------
    test("file annotation + declaration annotation: distinguished by kind") {
        val result = compile(
            SourceFile.kotlin(
                "FileMix.kt",
                """
                @file:Mixed

                package example.mixed

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Mixed(val kind: CaptureKind = CaptureKind())

                @Mixed
                fun pureFn() = 1

                internal object Main {
                    fun captured(): List<Mixed> = capturedSources<Mixed>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.mixed.Main")
        captured shouldHaveSize 2
        val kinds = captured.map { marker ->
            val kindFiller = fillerAnnotation(marker as Annotation, "kind")
            (annotationProperty(kindFiller, "value") as Enum<*>).name
        }.toSet()
        kinds shouldBe setOf("FILE", "FUNCTION")
    }
})
