package me.tbsten.capture.code.feature.markerDefinition.ir

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * bug-008 (3): capture 対象外の位置に付いた marker への
 * `CC_MARKER_ON_NON_CAPTURABLE_TARGET` warning
 * ([me.tbsten.capture.code.feature.markerDefinition.ir.warnIfNonCapturableMarkerUse.WarnIfNonCapturableMarkerUse])
 * の end-to-end 検証。
 *
 * - use-site target (`@get:Marker` / `@set:Marker`) 付き → property accessor に annotation が
 *   移り、 declaration collector は accessor を skip するため capture 0 件 → warning
 * - enum entry (`@Marker RED,`) → declaration walk の対象外のため capture 0 件 → warning
 * - 通常の declaration marker → warning は出ない (capture される)
 */
class WarnIfNonCapturableMarkerUseTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    test("use-site target 付き marker (get) は capture されない旨の warning が出る") {
        val result = compile(
            SourceFile.kotlin(
                "GetterTarget.kt",
                """
                package example.noncapturable_getter

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @get:Snippet
                internal val target: String = "body"

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldContain "does not capture"
        result.messages shouldContain "example.noncapturable_getter.Snippet"
        result.messages shouldContain "property accessor"
    }

    test("enum entry に付いた marker は capture されない旨の warning が出る") {
        val result = compile(
            SourceFile.kotlin(
                "EnumEntry.kt",
                """
                package example.noncapturable_enum

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                internal enum class Color {
                    @Snippet
                    RED,
                    GREEN,
                }

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldContain "does not capture"
        result.messages shouldContain "example.noncapturable_enum.Snippet"
        result.messages shouldContain "enum entry"
    }

    test("通常の declaration に付いた marker には warning が出ない") {
        val result = compile(
            SourceFile.kotlin(
                "NormalDeclaration.kt",
                """
                package example.noncapturable_normal

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Snippet
                internal val target: String = "body"

                @Snippet
                internal fun targetFn() = "fn body"

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "does not capture"
    }
})
