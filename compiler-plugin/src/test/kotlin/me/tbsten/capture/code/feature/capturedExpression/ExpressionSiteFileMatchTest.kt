package me.tbsten.capture.code.feature.capturedExpression

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * expression site の **file 突合** に関する regression test。
 *
 * `CaptureCodeExpressionSiteRegistry.Site` は FIR phase で `(filePath, startOffset, endOffset)`
 * を push し、 IR phase の `collectExpressionSites` が `CollectDeclarationSite.matchesFile` で
 * 「当該 IrFile に属する site か」 を判定する。 ここで **basename (leaf) だけの一致** を許すと、
 * `a/Basic.kt` と `b/Basic.kt` のように **同名 file が別ディレクトリに複数ある** 実プロジェクト
 * (e.g. feature ごとに `Basic.kt` を置く test module) で、 file A の offset が file B の text に
 * 適用され **無関係な位置の文字列が capture される**。
 */
class ExpressionSiteFileMatchTest : FunSpec({

    beforeEach {
        CaptureCodeMarkerRegistry.reset()
        CaptureCodeExpressionSiteRegistry.reset()
    }

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
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

    fun fillerAnnotation(marker: Annotation, fillerMethodName: String): Annotation {
        val method = marker.annotationClass.java.getMethod(fillerMethodName)
        return method.invoke(marker) as Annotation
    }

    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    // ----------------------------------------------------------------
    // 同名 file (別ディレクトリ) が同一 compilation にある場合、 expression site は
    // **自 file にのみ** 適用されること。
    // ----------------------------------------------------------------
    test("expression site does not leak into a same-named file in another directory") {
        val result = compile(
            SourceFile.kotlin(
                "featureA/Basic.kt",
                """
                package example.dupname.a

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DupMarker(val source: Source = Source())

                internal val sum = @DupMarker() (1 + 2 + 3)

                internal object Main {
                    fun captured(): List<DupMarker> = capturedSources<DupMarker>()
                }
                """.trimIndent(),
            ),
            // 同じ leaf 名 `Basic.kt` を持つ別ディレクトリの file。
            // featureA/Basic.kt より **長い** ので、 leaf 一致で誤マッチすると
            // offset が range 内に収まり garbage text が capture されてしまう。
            SourceFile.kotlin(
                "featureB/Basic.kt",
                """
                package example.dupname.b

                // このファイルには marker annotation が一切付いていない。
                // にもかかわらず capture されるようなら file 突合の bug。
                class UnrelatedClassAlpha(val alpha: String, val beta: Int, val gamma: Boolean)

                class UnrelatedClassBravo(val delta: String, val epsilon: Int, val zeta: Boolean)

                class UnrelatedClassCharlie(val eta: String, val theta: Int, val iota: Boolean)

                fun unrelatedFunction(): String = "unrelated body that should never be captured"
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.dupname.a.Main")
        val sources = captured.map { annotationProperty(fillerAnnotation(it as Annotation, "source"), "value") }
        sources shouldBe listOf("1 + 2 + 3")
        captured shouldHaveSize 1
    }
})
