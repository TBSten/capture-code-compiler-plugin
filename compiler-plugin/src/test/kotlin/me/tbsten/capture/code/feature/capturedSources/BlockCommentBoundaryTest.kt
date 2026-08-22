package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * bug-003 の end-to-end regression test (kctfork)。
 *
 * 直前にプレーン block comment がある宣言を capture したとき、 KDoc lookup
 * ([me.tbsten.capture.code.feature.capturedSources.ir.normalize.findKDocExtendedStartOffset])
 * が block comment の終端 star-slash を KDoc 終端と誤検出し、 file 手前の無関係な KDoc まで
 * 遡って **前の宣言ごと** capture していた。 修正後は対象宣言のみが capture されることを固定する。
 *
 * pure function レベルの網羅は
 * [me.tbsten.capture.code.feature.capturedSources.ir.normalize.KDocLookupTest] を参照。
 */
class BlockCommentBoundaryTest : FunSpec({

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

    test("直前にプレーン block comment がある宣言は前の宣言を巻き込まず宣言のみ capture される") {
        val result = compile(
            SourceFile.kotlin(
                "BlockCommentBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                /** KDoc of previous declaration. */
                internal fun previous() = "previous body"

                /* plain block comment, not kdoc */
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
            "internal fun target() = \"target body\""
    }
})
