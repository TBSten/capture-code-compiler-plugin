package me.tbsten.capture.code

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * bug-007: registry (marker / expression site) の compile 入口 reset の検証。
 *
 * `CaptureCodeMarkerRegistry` / `CaptureCodeExpressionSiteRegistry` は process-global object で、
 * 従来は `CaptureCodeIrExtension.generate` の finally でのみ reset されていた。 そのため
 * **FIR error で IR phase に到達しなかった compile は registry に残骸を残し**、 同一 ClassLoader
 * (= kctfork の連続 compile) の次の compile を汚染していた:
 *
 * - 同一 file path の残骸 expression site → 二重 capture (offset がずれていれば garbage capture)
 * - 残骸 marker FqN の再 register → duplicate marker FQN warning の false positive
 *
 * `CaptureCodeCompilerPluginRegistrar.registerExtensions` (compile の入口) で両 registry を
 * reset することでこれらが解消されることを end-to-end で確認する。
 *
 * NOTE: 本テストは意図的に beforeEach での reset を行わない (残骸が残った状態からの回復を
 * テストするため。 入口 reset 自体が検証対象)。
 */
class RegistryLifecycleResetTest : FunSpec({

    fun compileIn(workingDir: File, vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.workingDir = workingDir
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

    fun sourceValueOf(marker: Any?): String {
        val ann = marker as Annotation
        val filler = ann.annotationClass.java.getMethod("source").invoke(ann) as Annotation
        return filler.annotationClass.java.getMethod("value").invoke(filler) as String
    }

    // ----------------------------------------------------------------
    // FIR error → IR 不到達 → 残骸 → 同一 file path で再 compile のシナリオ。
    //
    // 1 回目: filler parameter に default の無い marker (= CC_MARKER_FILLER_REQUIRES_DEFAULT
    //         の FIR error) + runWithCaptureCode block。 FIR error で IR phase に到達しない
    //         ため、 marker FqN と block site が registry に残る。
    // 2 回目: error を直した source を **同じ file path** で compile。
    //         入口 reset が無いと、 残骸 site による二重 capture と duplicate marker FQN
    //         warning の false positive が起きる。
    // ----------------------------------------------------------------
    test("FIR error で IR に到達しなかった compile の残骸が同一 file path の次の compile を汚染しない") {
        val dir = createTempDirectory("cc-registry-reset-").toFile()

        val broken = SourceFile.kotlin(
            "Probe.kt",
            """
            package example.registry_reset

            import me.tbsten.capture.code.CaptureCode
            import me.tbsten.capture.code.Source
            import me.tbsten.capture.code.capturedSources
            import me.tbsten.capture.code.runWithCaptureCode

            @CaptureCode
            @Retention(AnnotationRetention.SOURCE)
            internal annotation class Snippet(val source: Source)

            internal val first = runWithCaptureCode(Snippet::class) {
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            }

            internal object Main {
                fun captured(): List<Snippet> = capturedSources<Snippet>()
            }
            """.trimIndent(),
        )
        val firstResult = compileIn(dir, broken)
        firstResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR

        val fixed = SourceFile.kotlin(
            "Probe.kt",
            """
            package example.registry_reset

            import me.tbsten.capture.code.CaptureCode
            import me.tbsten.capture.code.Source
            import me.tbsten.capture.code.capturedSources
            import me.tbsten.capture.code.runWithCaptureCode

            @CaptureCode
            @Retention(AnnotationRetention.SOURCE)
            internal annotation class Snippet(val source: Source = Source())

            internal val first = runWithCaptureCode(Snippet::class) {
                "BBBB"
            }

            internal object Main {
                fun captured(): List<Snippet> = capturedSources<Snippet>()
            }
            """.trimIndent(),
        )
        val secondResult = compileIn(dir, fixed)
        secondResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        // 残骸 marker の再 register による duplicate marker FQN warning が出ないこと
        secondResult.messages shouldNotContain "share the FQN"

        // 残骸 expression site による二重 capture が起きず、 ちょうど 1 件だけ capture されること
        val captured = loadCaptured(secondResult, mainFqn = "example.registry_reset.Main")
        captured shouldHaveSize 1
        sourceValueOf(captured[0]) shouldBe "\"BBBB\""
    }

    // ----------------------------------------------------------------
    // 残骸を registry API で直接注入するバージョン (file path 非依存の再現)。
    // 入口 reset が無いと、 残骸 registration + 本 compile の registration で
    // 同 FQN が 2 回 register されたことになり duplicate warning が誤発火する。
    // ----------------------------------------------------------------
    test("registry に残骸 marker が残っていても compile 入口で reset され duplicate FQN warning が誤発火しない") {
        CaptureCodeMarkerRegistry.registerMarker(
            fqn = "example.registry_reset_stale.Snippet",
            sourceFilePath = "/stale/previous-compile/Probe.kt",
        )

        val result = compileIn(
            createTempDirectory("cc-registry-reset-stale-").toFile(),
            SourceFile.kotlin(
                "Probe.kt",
                """
                package example.registry_reset_stale

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Snippet
                internal fun target() = "body"

                internal object Main {
                    fun captured(): List<Snippet> = capturedSources<Snippet>()
                }
                """.trimIndent(),
            ),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "share the FQN"
        loadCaptured(result, mainFqn = "example.registry_reset_stale.Main") shouldHaveSize 1
    }
})
