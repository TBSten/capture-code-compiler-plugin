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
})
