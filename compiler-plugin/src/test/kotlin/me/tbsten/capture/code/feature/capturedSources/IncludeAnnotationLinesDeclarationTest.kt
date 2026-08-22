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
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * bug-009 regression test: `includeAnnotationLines` が **declaration 起源 capture** で機能する
 * ことを end-to-end で検証する。
 *
 * 修正前は `CaptureCodePluginConfigBridge.toDeclarationNormalizeOptions` が
 * `stripLeadingAnnotationLines = false` をハードコードし、 marker 行の除去も
 * `extractDeclarationSource` の `skipLeadingMarkerAnnotations` + markerRanges drop が option を
 * 見ずに常時実行していたため、 公開 DSL に載っている `includeAnnotationLines = true` が
 * declaration 起源では黙って no-op になっていた (file 起源のみ機能)。
 *
 * 修正後は `effective.includeAnnotationLines == true` のとき collector 側が marker 行の
 * skip / drop 自体をスキップし、 `@Marker` 行がそのまま capture に含まれる。
 *
 * config 差し替え compile は [DslOptionPairwiseTest] 末尾の `PairwiseTestRegistrar` パターンを踏襲。
 */
class IncludeAnnotationLinesDeclarationTest : FunSpec({

    test("includeAnnotationLines = true なら宣言 capture に marker 行が含まれる") {
        val result = compileWithConfig(
            CaptureCodePluginConfig(includeAnnotationLines = true),
            SourceFile.kotlin(
                "IncludeAnnotOn.kt",
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
        sourceOf(captured[0] as Annotation) shouldBe
            "@Snippet\ninternal fun target() = \"target body\""
    }

    test("includeAnnotationLines = false (既定) なら従来通り marker 行が除去される") {
        val result = compileWithConfig(
            CaptureCodePluginConfig(includeAnnotationLines = false),
            SourceFile.kotlin(
                "IncludeAnnotOff.kt",
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
        sourceOf(captured[0] as Annotation) shouldBe "internal fun target() = \"target body\""
    }

    test("per-marker override Override.Yes でも global = false を上書きして marker 行が含まれる") {
        val result = compileWithConfig(
            CaptureCodePluginConfig(includeAnnotationLines = false),
            SourceFile.kotlin(
                "IncludeAnnotOverride.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(includeAnnotationLines = CaptureCode.Override.Yes)
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

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
        sourceOf(captured[0] as Annotation) shouldBe
            "@Snippet\ninternal fun target() = \"target body\""
    }

    test("includeAnnotationLines = true で marker と非 marker annotation が両方保持される") {
        // marker 行と非 marker (`@Suppress`) 行が混在するケース。 true なら両方とも source に残る
        // (false のときは marker 行だけ drop され `@Suppress` は残る = NonFirstMarkerLeakTest 側で検証済)。
        val result = compileWithConfig(
            CaptureCodePluginConfig(includeAnnotationLines = true),
            SourceFile.kotlin(
                "IncludeAnnotMixed.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippet(val source: Source = Source())

                @Suppress("unused")
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
        val src = sourceOf(captured[0] as Annotation)
        src shouldContain "@Suppress(\"unused\")"
        src shouldContain "@Snippet"
        src shouldNotContain "@CaptureCode"
    }
})

// ----------------------------------------------------------------------------
// DslOptionPairwiseTest の PairwiseTestRegistrar パターンを踏襲した config 注入 compile helper。
// registrar を private で複製しているのは kctfork の CompilerPluginRegistrar interface が
// compat module 間で安定していないため (詳細は DslOptionPairwiseTest 末尾コメント参照)。
// ----------------------------------------------------------------------------
private class IncludeAnnotationLinesTestRegistrar(
    private val config: CaptureCodePluginConfig,
) : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.put(CAPTURE_CODE_PLUGIN_CONFIG_KEY, config)
        FirExtensionRegistrarAdapter.registerExtension(CaptureCodeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(CaptureCodeIrExtension(config))
    }
}

private fun compileWithConfig(
    config: CaptureCodePluginConfig,
    vararg sources: SourceFile,
): JvmCompilationResult =
    KotlinCompilation().apply {
        this.sources = sources.toList()
        compilerPluginRegistrars = listOf(IncludeAnnotationLinesTestRegistrar(config))
        inheritClassPath = true
        jvmTarget = "17"
        messageOutputStream = System.out
    }.compile()

private fun loadCaptured(result: JvmCompilationResult): List<*> {
    val mainClass = result.classLoader.loadClass("example.Main")
    val mainInstance = mainClass.getField("INSTANCE").get(null)
    return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
}

private fun sourceOf(marker: Annotation): String {
    val src = marker.annotationClass.java.getMethod("source").invoke(marker)
    return src.javaClass.getMethod("value").invoke(src) as String
}
