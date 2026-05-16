package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CAPTURE_CODE_PLUGIN_CONFIG_KEY
import me.tbsten.capture.code.CaptureCodeFirExtensionRegistrar
import me.tbsten.capture.code.CaptureCodeIrExtension
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.effectiveFor
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Per-marker override 用テスト registrar。 production の `CaptureCodeCompilerPluginRegistrar` は
 * `CompilerConfiguration` の [CAPTURE_CODE_PLUGIN_CONFIG_KEY] を読むので、 kctfork から config を
 * 注入する経路を作る (kctfork は `CompilerConfiguration` への put を公開していないため、 ここで
 * registrar 自身が put を行う)。
 */
private class TestRegistrar(
    private val config: CaptureCodePluginConfig,
) : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.put(CAPTURE_CODE_PLUGIN_CONFIG_KEY, config)
        FirExtensionRegistrarAdapter.registerExtension(CaptureCodeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(CaptureCodeIrExtension(config))
    }
}

/**
 * `@CaptureCode(includeKdoc = Override.Yes, ...)` のような per-marker option override
 * (Logic A 拡張) を end-to-end で検証する unit test。
 *
 * marker 単位で global Gradle DSL config を上書きできること、 引数なしの `@CaptureCode`
 * は完全互換 (= global config に従う) であることを kctfork (kotlin-compile-testing fork)
 * で実コンパイルして確認する。
 */
class PerMarkerOptionOverrideTest : FunSpec({

    fun compile(
        config: CaptureCodePluginConfig = CaptureCodePluginConfig.DEFAULT,
        vararg sources: SourceFile,
    ): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(TestRegistrar(config))
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    fun loadCaptured(result: JvmCompilationResult, mainFqn: String = "example.Main"): List<*> {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    fun source(marker: Annotation): String {
        val src = marker.annotationClass.java.getMethod("source").invoke(marker)
        return src.javaClass.getMethod("value").invoke(src) as String
    }

    fun locationStartLine(marker: Annotation): Int {
        val loc = marker.annotationClass.java.getMethod("location").invoke(marker)
        return loc.javaClass.getMethod("startLine").invoke(loc) as Int
    }

    // ------------------------------------------------------------------
    // 0. resolver (pure function): SSOT を直接突くキャプチャされない場合の挙動
    // ------------------------------------------------------------------
    test("effectiveFor: Override.Yes forces true, Override.No forces false, Override.Default keeps config") {
        val config = CaptureCodePluginConfig(
            includeKdoc = false,
            dedent = true,
            includeLineInfo = true,
            includeImports = false,
            includeAnnotationLines = false,
        )
        val options = CaptureCodeMarkerOptions(
            includeKdoc = CaptureCodeMarkerOptions.Override.Yes,
            dedent = CaptureCodeMarkerOptions.Override.No,
            includeLineInfo = CaptureCodeMarkerOptions.Override.Default,
        )
        val effective = config.effectiveFor(options)
        effective.includeKdoc shouldBe true
        effective.dedent shouldBe false
        // Default → config に従う
        effective.includeLineInfo shouldBe true
        effective.includeImports shouldBe false
        effective.includeAnnotationLines shouldBe false
    }

    test("effectiveFor: DEFAULT options return the same config instance (fast-path)") {
        val config = CaptureCodePluginConfig(includeKdoc = false)
        val effective = config.effectiveFor(CaptureCodeMarkerOptions.DEFAULT)
        (effective === config) shouldBe true
    }

    // ------------------------------------------------------------------
    // 1. marker level `includeKdoc = Yes` で KDoc が含まれる (global config = false)
    // ------------------------------------------------------------------
    test("marker level includeKdoc=Yes wins over global config includeKdoc=false") {
        val result = compile(
            CaptureCodePluginConfig(includeKdoc = false),
            SourceFile.kotlin(
                "DocOverride.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(includeKdoc = CaptureCode.Override.Yes)
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DocSample(val source: Source = Source())

                /**
                 * Sample doc.
                 */
                @DocSample
                internal fun greet(): String = "hi"

                internal object Main {
                    fun captured(): List<DocSample> = capturedSources<DocSample>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val src = source(captured[0] as Annotation)
        src shouldContain "Sample doc"
        src shouldContain "internal fun greet"
    }

    // ------------------------------------------------------------------
    // 2. marker level `dedent = No` で global config (true) に逆らって indent 保持
    // ------------------------------------------------------------------
    test("marker level dedent=No wins over global config dedent=true") {
        val result = compile(
            CaptureCodePluginConfig(dedent = true),
            SourceFile.kotlin(
                "RawOverride.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(dedent = CaptureCode.Override.No)
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class RawSnippet(val source: Source = Source())

                internal class Outer {
                    @RawSnippet
                    internal fun indented(): String {
                        return "x"
                    }
                }

                internal object Main {
                    fun captured(): List<RawSnippet> = capturedSources<RawSnippet>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val src = source(captured[0] as Annotation)
        // dedent=false なので 元のインデント (4 space) が保持される
        src shouldContain "    internal fun indented(): String {"
    }

    // ------------------------------------------------------------------
    // 3. marker level `includeLineInfo = No` で startLine が 0 に倒れる
    // ------------------------------------------------------------------
    test("marker level includeLineInfo=No zeroes startLine / endLine") {
        val result = compile(
            CaptureCodePluginConfig(includeLineInfo = true),
            SourceFile.kotlin(
                "LineOverride.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(includeLineInfo = CaptureCode.Override.No)
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class NoLine(
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                )

                @NoLine
                internal val flag = true

                internal object Main {
                    fun captured(): List<NoLine> = capturedSources<NoLine>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // includeLineInfo=false で startLine=0 (filler の design 値域)
        locationStartLine(captured[0] as Annotation) shouldBe 0
    }

    // ------------------------------------------------------------------
    // 4. parameter なしの `@CaptureCode` は完全互換 (= global config に従う)
    // ------------------------------------------------------------------
    test("parameter-less @CaptureCode follows the global config (backward compat)") {
        val result = compile(
            CaptureCodePluginConfig(includeKdoc = false),
            SourceFile.kotlin(
                "Legacy.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Legacy(val source: Source = Source())

                /**
                 * Legacy doc.
                 */
                @Legacy
                internal fun legacy(): String = "x"

                internal object Main {
                    fun captured(): List<Legacy> = capturedSources<Legacy>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val src = source(captured[0] as Annotation)
        // global config で includeKdoc=false なので KDoc は含まれない
        src shouldNotContain "Legacy doc"
    }

    // ------------------------------------------------------------------
    // 5. 複数 override を同時に指定
    // ------------------------------------------------------------------
    test("multiple overrides apply independently") {
        val result = compile(
            CaptureCodePluginConfig(includeKdoc = false, dedent = true),
            SourceFile.kotlin(
                "Multi.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(
                    includeKdoc = CaptureCode.Override.Yes,
                    dedent = CaptureCode.Override.No,
                )
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MultiOver(val source: Source = Source())

                internal class Outer {
                    /**
                     * Multi doc.
                     */
                    @MultiOver
                    internal fun indented(): String {
                        return "y"
                    }
                }

                internal object Main {
                    fun captured(): List<MultiOver> = capturedSources<MultiOver>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val src = source(captured[0] as Annotation)
        src shouldContain "Multi doc"
        src shouldContain "    internal fun indented(): String {"
    }
})
