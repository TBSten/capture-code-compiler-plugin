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

    test("source with hardcoded marker (@com.example.Snippets) on a property compiles successfully") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package com.example

                annotation class Snippets
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.example

                @Snippets
                val greeting = "hello"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("property without the hardcoded marker still compiles successfully (marker absent path)") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package com.example

                annotation class Snippets
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.example

                val plain = "hello"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("annotation with different FqN does not break compilation (FqN mismatch path)") {
        val result = compile(
            SourceFile.kotlin(
                "OtherSnippets.kt",
                """
                package com.other

                annotation class Snippets
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.other

                @Snippets
                val skipped = "ignored"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    // task-006: capturedSources<Snippets>() の書き換え結果を runtime 実行で確認する。
    // hardcoded marker (`com.example.Snippets`) の付いた property 1 件があるソースをコンパイルし、
    // 生成された Main.captured() を反射で呼んで戻り値が listOf(Snippets(Source(value = "..."))) になることを確認する。
    test("capturedSources<Snippets>() is rewritten to a list literal at IR phase") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package com.example

                annotation class Snippets(val source: me.tbsten.capture.code.Source = me.tbsten.capture.code.Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.example

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

        val mainClass = result.classLoader.loadClass("com.example.Main")
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

    // R7 検証: SOURCE retention でも annotation class 自体は class file に残るので、
    // `Snippets` の constructor symbol は通常通り解決でき、`Snippets(...)` の IR 構築が成立する。
    test("capturedSources<Snippets>() works even when Snippets has @Retention(SOURCE)") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package com.example

                @Retention(AnnotationRetention.SOURCE)
                annotation class Snippets(val source: me.tbsten.capture.code.Source = me.tbsten.capture.code.Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.example

                import me.tbsten.capture.code.capturedSources

                @Snippets
                val sourceRetained = "kept"

                object Main {
                    fun captured(): List<Snippets> = capturedSources<Snippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("com.example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>
        captured.size shouldBe 1
    }

    test("capturedSources<Snippets>() is rewritten to an empty list when no marker is present") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package com.example

                annotation class Snippets(val source: me.tbsten.capture.code.Source = me.tbsten.capture.code.Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Usage.kt",
                """
                package com.example

                import me.tbsten.capture.code.capturedSources

                val plain = "no marker here"

                object Main {
                    fun captured(): List<Snippets> = capturedSources<Snippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val mainClass = result.classLoader.loadClass("com.example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>

        captured.size shouldBe 0
    }
})
