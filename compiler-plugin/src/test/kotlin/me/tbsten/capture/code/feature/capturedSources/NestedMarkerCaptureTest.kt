package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * **nested marker** (object / object 多段の中に宣言した `@CaptureCode` annotation class) の
 * end-to-end capture を kctfork で検証する。
 *
 * bug-002 (nested marker が rewrite されず runtime 例外) の regression test:
 * registry の marker FqN は FIR 側で `classId.asSingleFqName().asString()` に flatten される
 * (`example.Ns.Snippet`) ため、 IR 側で `ClassId.topLevel(...)` 固定で resolve すると nested
 * marker が `null` になり、 rewrite が silent skip → 実行時に
 * `IllegalStateException: CaptureCode compiler plugin is not applied` になっていた。
 * 修正後は `referenceMarkerClass` が flatten FqN の分割候補を総当たりして resolve する。
 *
 * top-level marker の regression は [AllDeclarationTargetsTest] 等の既存テストで担保する。
 */
class NestedMarkerCaptureTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /** `capturedSources<T>()` の戻り値の annotation から `source.value` を取り出すヘルパ。 */
    fun captureSourceValue(annotation: Annotation): String {
        val sourceMethod = annotation.annotationClass.java.getMethod("source")
        val sourceAnnotation = sourceMethod.invoke(annotation) as Annotation
        val valueMethod = sourceAnnotation.annotationClass.java.getMethod("value")
        return valueMethod.invoke(sourceAnnotation) as String
    }

    /** capture サイトを返す `Main.captured()` を呼び出して `List<*>` を取得するヘルパ。 */
    fun loadCaptured(result: JvmCompilationResult): List<*> {
        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    test("object 内の nested marker を qualified 参照 (@Ns.Snippet) しても capture できる") {
        val result = compile(
            SourceFile.kotlin(
                "NestedQualified.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal object Ns {
                    @CaptureCode
                    @Target(AnnotationTarget.FUNCTION)
                    @Retention(AnnotationRetention.SOURCE)
                    internal annotation class Snippet(val source: Source = Source())
                }

                @Ns.Snippet
                internal fun target() = "target body"

                internal object Main {
                    fun captured(): List<Ns.Snippet> = capturedSources<Ns.Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "rewrite failed"

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // bug-005 (qualified name の末尾 segment 照合) と組み合わさることで、
        // `@Ns.Snippet` の qualified 参照でも marker 行は capture から除去される。
        captureSourceValue(captured[0] as Annotation) shouldBe
            "internal fun target() = \"target body\""
    }

    test("object 内の nested marker を direct import (@Snippet) で使っても capture できる") {
        val result = compile(
            SourceFile.kotlin(
                "NestedImported.kt",
                """
                package example

                import example.Ns.Snippet
                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal object Ns {
                    @CaptureCode
                    @Target(AnnotationTarget.FUNCTION)
                    @Retention(AnnotationRetention.SOURCE)
                    internal annotation class Snippet(val source: Source = Source())
                }

                @Snippet
                internal fun target() = "target body"

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "rewrite failed"

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "internal fun target() = \"target body\""
    }

    test("2 段 nested (object A { object B { ... } }) の marker も capture できる") {
        val result = compile(
            SourceFile.kotlin(
                "DoublyNested.kt",
                """
                package example

                import example.A.B.S
                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal object A {
                    internal object B {
                        @CaptureCode
                        @Target(AnnotationTarget.FUNCTION)
                        @Retention(AnnotationRetention.SOURCE)
                        internal annotation class S(val source: Source = Source())
                    }
                }

                @S
                internal fun target() = "target body"

                internal object Main {
                    fun captured(): List<S> = capturedSources<S>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "rewrite failed"

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "internal fun target() = \"target body\""
    }
})
