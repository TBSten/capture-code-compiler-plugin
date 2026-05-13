package me.tbsten.capture.code

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CaptureCodeCompilerPluginTest : FunSpec({

    fun compile(source: String): JvmCompilationResult =
        KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Source.kt", source))
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    test("simple source compiles successfully with plugin enabled") {
        val result = compile(
            """
            package example

            fun main() {
                println("hello")
            }
            """.trimIndent(),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }
})
