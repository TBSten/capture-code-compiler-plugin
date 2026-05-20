package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite

/**
 * BUG-B (Charter 1 — prev-property-leak) regression test:
 * 同一行に annotation + property (= `@Marker val x = 1`) の形式で複数 property が連続するとき、
 * 2 件目以降の `Source.value` に **前の declaration line** が leak しないことを verify する。
 *
 * BUG-A (`task-129`) 修正後の境界調査で発見した別の bug。 `isModifierOrAnnotationLine` が
 * 「行が `@` で始まる = annotation 行」 と単純判定していたため、 `@Marker val a = 1` 行を
 * annotation 行と誤判定し、 `expandStartToCoverModifierAndAnnotationLines` の遡りループが
 * 前の declaration line まで吸収してしまっていた。
 *
 * 修正 (`isModifierOrAnnotationLine` に declaration keyword 検出を追加) 後は、 同一行に
 * `val` / `var` / `fun` / `class` 等を含む行は declaration 行扱いとなり遡り停止する。
 */
class PrevPropertyLeakTest : FunSpec({

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

    fun loadCaptured(className: String, methodName: String, result: JvmCompilationResult): List<*> {
        val mainClass = result.classLoader.loadClass(className)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod(methodName).invoke(mainInstance) as List<*>
    }

    // ----------------------------------------------------------------
    // unit-level: `isModifierOrAnnotationLine` が同一行 annotation + declaration keyword
    // のときに annotation 行と判定しないことを直接確認 (内部は private のため、 公開 helper
    // `expandStartToCoverModifierAndAnnotationLines` 経由で間接的に verify)。
    // ----------------------------------------------------------------
    test("expandStartToCoverModifierAndAnnotationLines stops at line containing declaration keyword") {
        val site = CollectDeclarationSite()
        // 行 1: "    @M val a = 1"  (= 0..16, + \n = 17)
        // 行 2: "    @M val b = 2"  (= 17..33)
        val text = "    @M val a = 1\n    @M val b = 2"
        // property b の startOffset を `@M` 直前 (= 21) として呼び出し
        val expanded = site.expandStartToCoverModifierAndAnnotationLines(text, 21)
        // 期待: line 2 の頭 (= 17) まで遡って停止し、 line 1 まで遡らない
        expanded shouldBe 17
    }

    // ----------------------------------------------------------------
    // BUG-B repro: 同 marker、 1 行 1 property (連続 2 件) で source leak しないこと
    // ----------------------------------------------------------------
    test("same marker on consecutive single-line properties keeps each source isolated") {
        val result = compile(
            SourceFile.kotlin(
                "SameMarkerProps.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SamePropMarker(val source: Source = Source())

                internal object Main {
                    @SamePropMarker val a = 1
                    @SamePropMarker val b = 2
                    fun captured(): List<SamePropMarker> = capturedSources<SamePropMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured("example.Main", "captured", result)
        captured.size shouldBe 2
        val sources = captured.map { captureSourceValue(it as Annotation) }
        sources.toSet() shouldBe setOf("val a = 1", "val b = 2")
    }

    // ----------------------------------------------------------------
    // 異 marker、 1 行 1 property の境界: 異 marker でも前 declaration leak しないこと
    // ----------------------------------------------------------------
    test("different markers on consecutive single-line properties keep each source isolated") {
        val result = compile(
            SourceFile.kotlin(
                "DifferentMarkersProps.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MarkerX(val source: Source = Source())

                @CaptureCode
                @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MarkerY(val source: Source = Source())

                internal object Main {
                    @MarkerX val a = 1
                    @MarkerY val b = 2
                    fun capturedX(): List<MarkerX> = capturedSources<MarkerX>()
                    fun capturedY(): List<MarkerY> = capturedSources<MarkerY>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val capturedX = loadCaptured("example.Main", "capturedX", result)
        val capturedY = loadCaptured("example.Main", "capturedY", result)
        capturedX.size shouldBe 1
        capturedY.size shouldBe 1
        captureSourceValue(capturedX[0] as Annotation) shouldBe "val a = 1"
        captureSourceValue(capturedY[0] as Annotation) shouldBe "val b = 2"
    }

    // ----------------------------------------------------------------
    // 1 個目だけ marker / 2 個目は非 marker のケース: 2 個目が site に含まれず、 1 個目の
    // source も `val b = 2` の line を leak しないこと
    // ----------------------------------------------------------------
    test("marker on first property only leaks no source from following plain property") {
        val result = compile(
            SourceFile.kotlin(
                "FirstOnlyMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FirstMarker(val source: Source = Source())

                internal object Main {
                    @FirstMarker val a = 1
                    val b = 2
                    fun captured(): List<FirstMarker> = capturedSources<FirstMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured("example.Main", "captured", result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "val a = 1"
    }

    // ----------------------------------------------------------------
    // sanity: 別行 annotation (= 改行で annotation と declaration を分離) では引き続き
    // 正しく動作する。 これは BUG-A 既存テストと同じ形だが、 BUG-B 修正で regression が
    // ないことを担保するための保険。
    // ----------------------------------------------------------------
    test("annotation on separate line keeps existing extraction behavior") {
        val result = compile(
            SourceFile.kotlin(
                "SeparateLineAnnotation.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class SeparateMarker(val source: Source = Source())

                internal object Main {
                    @SeparateMarker
                    val a = 1

                    @SeparateMarker
                    val b = 2

                    fun captured(): List<SeparateMarker> = capturedSources<SeparateMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured("example.Main", "captured", result)
        captured.size shouldBe 2
        val sources = captured.map { captureSourceValue(it as Annotation) }
        sources.toSet() shouldBe setOf("val a = 1", "val b = 2")
    }
})
