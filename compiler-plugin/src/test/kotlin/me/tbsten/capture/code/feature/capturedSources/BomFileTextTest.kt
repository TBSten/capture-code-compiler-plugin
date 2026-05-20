package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * Regression test: UTF-8 BOM (U+FEFF) を file 先頭に持つ Kotlin source に対しても、
 * declaration site の source 抽出が off-by-one せずに完全な declaration を返すこと。
 *
 * Charter 9 (extreme input fuzzing) F6 で発見した bug。 修正前は:
 * - `CompatContextImpl.loadFileText` が PSI 経由で取得した text に BOM が含まれていた一方、
 *   IR の startOffset / endOffset は BOM を含まない座標系で計算されていたため、 全 declaration
 *   起源 source 抽出が 1 char 左にずれていた
 * - 結果: marker annotation 行が source に leak + 末尾 1 char (e.g. 閉じ `"`) が欠落
 *
 * 修正 (= `CollectDeclarationSite.invoke` の `cachedFileText` で先頭 BOM を strip) 後は、
 * BOM 付き / なし どちらの file でも同一の captured source が得られる。
 */
class BomFileTextTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    fun captureSourceValue(annotation: Annotation): String {
        val sourceMethod = annotation.annotationClass.java.getMethod("source")
        val sourceAnnotation = sourceMethod.invoke(annotation) as Annotation
        val valueMethod = sourceAnnotation.annotationClass.java.getMethod("value")
        return valueMethod.invoke(sourceAnnotation) as String
    }

    fun loadCaptured(result: JvmCompilationResult): List<*> {
        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    test("BOM at file start does not shift declaration source offsets") {
        // 先頭に BOM (U+FEFF) を明示的に置く。 SourceFile.kotlin の contents 引数は String で
        // 渡されるので、 raw Kotlin file の `0xEF 0xBB 0xBF` 相当を Kotlin string で表現する
        // (= U+FEFF 1 char)。
        val bomPrefixedSource = "﻿" + """
            package example

            import me.tbsten.capture.code.CaptureCode
            import me.tbsten.capture.code.Source
            import me.tbsten.capture.code.capturedSources

            @CaptureCode
            @Target(AnnotationTarget.PROPERTY)
            @Retention(AnnotationRetention.SOURCE)
            internal annotation class BomMarker(val source: Source = Source())

            @BomMarker
            val bomDeclared = "after BOM"

            internal object Main {
                fun captured(): List<BomMarker> = capturedSources<BomMarker>()
            }
        """.trimIndent()

        val result = compile(SourceFile.kotlin("BomFile.kt", bomPrefixedSource))
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "val bomDeclared = \"after BOM\""
    }
})
