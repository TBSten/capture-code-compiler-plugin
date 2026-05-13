package me.tbsten.capture.code

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CaptureCodeCompilerPluginTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    // ----------------------------------------------------------------
    // 基本: plugin enabled でも通常のコードは普通にコンパイルできる
    // ----------------------------------------------------------------
    test("simple source compiles successfully with plugin enabled") {
        val result = compile(
            SourceFile.kotlin(
                "Source.kt",
                """
                package example

                fun main() {
                    println("hello")
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    // ----------------------------------------------------------------
    // Logic A (task-008): @CaptureCode メタ付き annotation class が登録され、
    // それを付けた property をキャプチャできる (動的検出 + Logic H 連携の最小ケース)
    // ----------------------------------------------------------------
    test("dynamically discovered marker (@CaptureCode meta) on a property captures source") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                annotation class Snippets(val source: Source = Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package example

                import me.tbsten.capture.code.capturedSources

                @Snippets
                val greeting = "hello"

                object Main {
                    fun captured(): List<Snippets> = capturedSources<Snippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>

        captured.size shouldBe 1
        val snippetsAnnotation = captured[0] as Annotation
        val sourceMethod = snippetsAnnotation.annotationClass.java.getMethod("source")
        val sourceAnnotation = sourceMethod.invoke(snippetsAnnotation) as Annotation
        val valueMethod = sourceAnnotation.annotationClass.java.getMethod("value")
        val sourceValue = valueMethod.invoke(sourceAnnotation) as String
        sourceValue shouldBe "val greeting = \"hello\""
    }

    // ----------------------------------------------------------------
    // Logic A: @CaptureCode メタが付いていない annotation class は marker として認識されない
    // → 付与された property はキャプチャ対象外。`capturedSources<T>()` の書き換えも起きないので
    //   plugin 未適用時と同じ `error("CaptureCode compiler plugin is not applied")` が runtime に投げられる前提
    //   ここでは compile が通る (warning/error 無し) ことだけを確認する
    // ----------------------------------------------------------------
    test("plain annotation class without @CaptureCode is not treated as marker") {
        val result = compile(
            SourceFile.kotlin(
                "PlainAnnotation.kt",
                """
                package example

                // @CaptureCode を付けない通常の annotation class
                annotation class NotAMarker

                @NotAMarker
                val ignored = "this should not be captured"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    // ----------------------------------------------------------------
    // Logic A: visibility は registry の登録条件ではない (Logic F の責務)。
    // internal / private いずれの marker でも動的検出されることを確認する。
    // ----------------------------------------------------------------
    test("internal marker is discovered") {
        val result = compile(
            SourceFile.kotlin(
                "InternalMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class InternalSnippets(val source: Source = Source())

                @InternalSnippets
                internal val internalProp = "internal"

                internal object InternalMain {
                    fun captured(): List<InternalSnippets> = capturedSources<InternalSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("example.InternalMain")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>
        captured.size shouldBe 1
    }

    test("private marker is also discovered") {
        // private な top-level annotation class は同 file 内からのみ参照可能。
        // marker / use-site / `capturedSources<T>()` 呼び出しを 1 file にまとめる。
        val result = compile(
            SourceFile.kotlin(
                "PrivateMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                private annotation class PrivateSnippets(val source: Source = Source())

                @PrivateSnippets
                private val privateProp = "private"

                private object PrivateMain {
                    fun captured(): List<PrivateSnippets> = capturedSources<PrivateSnippets>()
                }

                // file-private なので外から呼ぶ public bridge
                object PrivateBridge {
                    fun size(): Int = PrivateMain.captured().size
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val bridgeClass = result.classLoader.loadClass("example.PrivateBridge")
        val bridge = bridgeClass.getField("INSTANCE").get(null)
        val size = bridgeClass.getMethod("size").invoke(bridge) as Int
        size shouldBe 1
    }

    // ----------------------------------------------------------------
    // Logic A: 複数の @CaptureCode marker が同一モジュールで定義されている場合、
    // それぞれが独立に検出される (registry に複数 ClassId が登録される)
    // ----------------------------------------------------------------
    test("multiple markers are discovered and captured independently") {
        val result = compile(
            SourceFile.kotlin(
                "MultiMarkers.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                annotation class Alpha(val source: Source = Source())

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                annotation class Beta(val source: Source = Source())

                @Alpha
                val a = "value-a"

                @Beta
                val b = "value-b"

                @Alpha
                val a2 = "value-a2"

                object Main {
                    fun alphas(): List<Alpha> = capturedSources<Alpha>()
                    fun betas(): List<Beta> = capturedSources<Beta>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)

        val alphas = mainClass.getMethod("alphas").invoke(mainInstance) as List<*>
        val betas = mainClass.getMethod("betas").invoke(mainInstance) as List<*>

        alphas.size shouldBe 2
        betas.size shouldBe 1
    }

    // ----------------------------------------------------------------
    // Logic H: capture 対象が 0 件の marker でも `listOf<T>()` (空) に書き換わる
    // ----------------------------------------------------------------
    test("capturedSources<T>() returns empty list when no marker is applied") {
        val result = compile(
            SourceFile.kotlin(
                "EmptyCase.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Retention(AnnotationRetention.SOURCE)
                annotation class Unused(val source: Source = Source())

                val plain = "no marker here"

                object Main {
                    fun captured(): List<Unused> = capturedSources<Unused>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>
        captured.size shouldBe 0
    }
})
