package me.tbsten.capture.code.feature.captured_sources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * filler 自動値埋め (`Source` / `SourceLocation` / `CaptureKind`) を
 * kctfork で end-to-end 検証する。
 *
 * 各テストは:
 * 1. plugin enabled で marker (`@CaptureCode` 付き、対象 filler のみを持つ) と use site と
 *    `capturedSources<T>()` を 1 file にまとめてコンパイル
 * 2. 生成された class file を reflection で読み、`capturedSources<T>()` が `listOf(Marker(...))` に
 *    書き換わって 1 件以上の annotation instance を返すこと、各 filler の値が想定通りに埋まって
 *    いることを assert
 *
 * `K200CapturedSourcesCollector` (collector) / `K200CapturedSourcesRewriter` (rewriter) /
 * `filler/SourceFillerBuilder` `SourceLocationFillerBuilder` `CaptureKindFillerBuilder`
 * の合流地点を確認する smoke test の集合。
 *
 * ## ユーザ定義パラメータとの境界
 *
 * 本テストの marker はいずれも **filler 型のみ** をパラメータに持つ。ユーザ定義パラメータが
 * 混在する場合の挙動は [UserArgIrBuilderTest] で確認する。
 */
class FillerBuilderTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /** capture サイトを返す `Main.captured()` を呼び出して `List<*>` を取得するヘルパ。 */
    fun loadCaptured(result: JvmCompilationResult, mainFqn: String = "example.Main"): List<*> {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    /**
     * marker annotation instance から filler annotation メソッド (例: `source()` / `location()` /
     * `kind()`) を呼び、入れ子の filler annotation instance を返す。
     */
    fun fillerAnnotation(marker: Annotation, fillerMethodName: String): Annotation {
        val method = marker.annotationClass.java.getMethod(fillerMethodName)
        return method.invoke(marker) as Annotation
    }

    /** filler annotation の単一プロパティ (例: `value()` / `packageName()`) を返す。 */
    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    // ----------------------------------------------------------------
    // 1. Source filler のみを持つ marker
    // ----------------------------------------------------------------
    test("Source filler only: value is filled with declaration text") {
        val result = compile(
            SourceFile.kotlin(
                "SourceOnly.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SrcOnly(val source: Source = Source())

                @SrcOnly
                internal fun greet(): String = "hi"

                internal object Main {
                    fun captured(): List<SrcOnly> = capturedSources<SrcOnly>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val sourceFiller = fillerAnnotation(marker, "source")
        annotationProperty(sourceFiller, "value") shouldBe "internal fun greet(): String = \"hi\""
    }

    // ----------------------------------------------------------------
    // 2. SourceLocation filler のみを持つ marker
    // ----------------------------------------------------------------
    test("SourceLocation filler only: packageName / filePath / lines are filled") {
        val result = compile(
            SourceFile.kotlin(
                "LocOnly.kt",
                """
                package example.sub

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LocOnly(val location: SourceLocation = SourceLocation())

                @LocOnly
                internal val flag = true

                internal object Main {
                    fun captured(): List<LocOnly> = capturedSources<LocOnly>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.sub.Main")
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val locationFiller = fillerAnnotation(marker, "location")
        annotationProperty(locationFiller, "packageName") shouldBe "example.sub"
        // filePath は kctfork 経由の絶対パス (temp dir) のため厳密 assert はしない。
        // 0-arg default 値 ("") から書き換わっていることを確認する。
        (annotationProperty(locationFiller, "filePath") as String).isNotEmpty() shouldBe true
        // startLine / endLine は 1-based。`@LocOnly\nval flag = ...` の `@LocOnly` 行を含めて
        // collector が skipLeadingAnnotationLines するため、実際の declaration 開始行は @LocOnly
        // 行の位置 (= ソース内の `@LocOnly` の行) になる。
        // 厳密値は test fixture 依存なので、`> 0` で書き換えが発生したことのみ確認する。
        val startLine = annotationProperty(locationFiller, "startLine") as Int
        val endLine = annotationProperty(locationFiller, "endLine") as Int
        (startLine > 0) shouldBe true
        (endLine > 0) shouldBe true
    }

    // ----------------------------------------------------------------
    // 3. CaptureKind filler のみを持つ marker
    // ----------------------------------------------------------------
    test("CaptureKind filler only: enum value matches the declaration kind") {
        val result = compile(
            SourceFile.kotlin(
                "KindOnly.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class KindOnly(val kind: CaptureKind = CaptureKind())

                @KindOnly
                internal val a = 1

                @KindOnly
                internal class B

                @KindOnly
                internal fun c() = 3

                internal object Main {
                    fun captured(): List<KindOnly> = capturedSources<KindOnly>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 3
        val kinds = captured.map { marker ->
            val kindFiller = fillerAnnotation(marker as Annotation, "kind")
            // CaptureKind.value() は enum (CaptureKind.Kind)。toString() / name で文字列化できる。
            val enumValue = annotationProperty(kindFiller, "value") as Enum<*>
            enumValue.name
        }
        kinds shouldContain "PROPERTY"
        kinds shouldContain "CLASS"
        kinds shouldContain "FUNCTION"
    }

    // ----------------------------------------------------------------
    // 4. 全 filler が同時に埋まる
    // ----------------------------------------------------------------
    test("all fillers populated at once") {
        val result = compile(
            SourceFile.kotlin(
                "FullCap.kt",
                """
                package example.full

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FullCap(
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                    val kind: CaptureKind = CaptureKind(),
                )

                @FullCap
                internal fun answer() = 42

                internal object Main {
                    fun captured(): List<FullCap> = capturedSources<FullCap>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.full.Main")
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        annotationProperty(fillerAnnotation(marker, "source"), "value") shouldBe
            "internal fun answer() = 42"
        annotationProperty(fillerAnnotation(marker, "location"), "packageName") shouldBe
            "example.full"
        val kindValue = annotationProperty(fillerAnnotation(marker, "kind"), "value") as Enum<*>
        kindValue.name shouldBe "FUNCTION"
    }
})
