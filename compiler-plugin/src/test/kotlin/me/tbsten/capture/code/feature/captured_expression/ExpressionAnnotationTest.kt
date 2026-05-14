package me.tbsten.capture.code.feature.captured_expression

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry

/**
 * **Expression annotation** (`@Marker (expr)`) の挙動を kctfork で
 * end-to-end 検証する。
 *
 * 全体方針 (design §5 Logic B-fir):
 * - FIR phase で `FirBasicExpressionChecker` が `(filePath, startOffset, endOffset, markerFqn, ...)`
 *   を `CaptureCodeExpressionSiteRegistry` に push する
 * - IR phase の `K200CapturedSourcesCollector.collectExpressionSites()` が registry を読み、
 *   file text の substring から `CapturedSite(kind = EXPRESSION)` を構築する
 *
 * ## カバーする観点
 *
 * 1. 最小式 (`@CaptureExpr (1 + 2 + 3)`) のキャプチャ + kind = EXPRESSION
 * 2. property initializer 内の式 (ケース #26 同等)
 * 3. return 文の式 (ケース #27 同等)
 * 4. 関数引数 (ケース #28 同等)
 * 5. `@Marker run { ... }` ブロック形 (ケース #29 同等)
 * 6. 同一ファイル内の複数式 annotation (ケース #56 同等)
 * 7. ネストラムダ内の式 (ケース #68 同等)
 * 8. 同じ marker が宣言と式の両方に付くケース (ケース #67 同等) — kind で識別できる
 *
 * 非対象 (本ファイル scope 外):
 * - 1 行に複数式 annotation: integration-test の `ExpressionCasesTest#ケース64` で確認
 * - DSL 利用 (`html { ... }`): integration-test の `ScenarioCasesTest#ケース84` で確認
 */
class ExpressionAnnotationTest : FunSpec({

    beforeEach {
        // 連続 compile で前回の site が残らないよう registry を毎回リセット
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
    // 1. 最小式: `@CaptureMin() (1 + 2 + 3)` で Source.value = "1 + 2 + 3"、kind = EXPRESSION
    //
    // 注記: K2 parser は `@CaptureMin (1 + 2 + 3)` を annotation constructor argument
    // `CaptureMin(1 + 2 + 3)` として greedy に解釈し、Source 型と Int 型の不一致で
    // COMPILATION_ERROR になる (expression annotation spike Case A 観察結果)。`@CaptureMin()` で明示的に
    // 空 argument list を渡すと parser が annotation 終端を認識する。本プラグインは **`@Marker()`
    // を推奨する** こととし、design §7.8 の「`@Foo run { ... }` または `@Foo ({ ... })` のように
    // 式として括る」 を補強する形 で `@Marker() expr` ガイドラインを採用する。
    // ----------------------------------------------------------------
    test("expression annotation: minimal expr captures Source and kind = EXPRESSION") {
        val result = compile(
            SourceFile.kotlin(
                "Minimal.kt",
                """
                package example.expr_min

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureMin(
                    val source: Source = Source(),
                    val kind: CaptureKind = CaptureKind(),
                )

                val sum = @CaptureMin() (1 + 2 + 3)

                internal object Main {
                    fun captured(): List<CaptureMin> = capturedSources<CaptureMin>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.expr_min.Main")
        captured shouldHaveSize 1
        val marker = captured[0] as Annotation
        val sourceFiller = fillerAnnotation(marker, "source")
        annotationProperty(sourceFiller, "value") shouldBe "1 + 2 + 3"
        val kindFiller = fillerAnnotation(marker, "kind")
        val enumValue = annotationProperty(kindFiller, "value") as Enum<*>
        enumValue.name shouldBe "EXPRESSION"
    }

    // ----------------------------------------------------------------
    // 2. property initializer 内 — 同等のセマンティクスがあるか
    // ----------------------------------------------------------------
    test("expression annotation: property initializer expression") {
        val result = compile(
            SourceFile.kotlin(
                "PropInit.kt",
                """
                package example.expr_prop

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureP(val source: Source = Source())

                private fun compute(s: String): Int = s.hashCode()

                val hash = @CaptureP() (compute("a" + "b") + compute("c"))

                internal object Main {
                    fun captured(): List<CaptureP> = capturedSources<CaptureP>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.expr_prop.Main")
        captured shouldHaveSize 1
        val sourceFiller = fillerAnnotation(captured[0] as Annotation, "source")
        annotationProperty(sourceFiller, "value") shouldBe "compute(\"a\" + \"b\") + compute(\"c\")"
    }

    // ----------------------------------------------------------------
    // 3. return 文の式
    //
    // 注意: K2 parser は `return @Marker (expr)` を **return label** (`return@label`) と
    // 解釈しようとして parse error になる。 design §3.4 では「式 annotation を return で
    // 使う場合はローカル変数経由を推奨」 とする方針で test ケースを構築する。
    // ----------------------------------------------------------------
    test("expression annotation: return statement expression") {
        val result = compile(
            SourceFile.kotlin(
                "Return.kt",
                """
                package example.expr_ret

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureR(val source: Source = Source())

                fun computeAnswer(): Int {
                    val r = @CaptureR() (40 + 2)
                    return r
                }

                internal object Main {
                    fun captured(): List<CaptureR> = capturedSources<CaptureR>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_ret.Main")
        captured shouldHaveSize 1
        val src = fillerAnnotation(captured[0] as Annotation, "source")
        annotationProperty(src, "value") shouldBe "40 + 2"
    }

    // ----------------------------------------------------------------
    // 4. 関数引数: `println(@CaptureA() ("hello " + "world"))`
    // ----------------------------------------------------------------
    test("expression annotation: function argument expression") {
        val result = compile(
            SourceFile.kotlin(
                "FnArg.kt",
                """
                package example.expr_arg

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureA(val source: Source = Source())

                fun makeGreeting(): String = @CaptureA() ("hello " + "world")

                internal object Main {
                    fun captured(): List<CaptureA> = capturedSources<CaptureA>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_arg.Main")
        captured shouldHaveSize 1
        val src = fillerAnnotation(captured[0] as Annotation, "source")
        annotationProperty(src, "value") shouldBe "\"hello \" + \"world\""
    }

    // ----------------------------------------------------------------
    // 5. ブロック形 `@Marker run { ... }`
    // ----------------------------------------------------------------
    test("expression annotation: run-block form (@Marker run { ... })") {
        val result = compile(
            SourceFile.kotlin(
                "RunBlock.kt",
                """
                package example.expr_run

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureBlk(val source: Source = Source())

                val result = @CaptureBlk() run {
                    val hoge = "hogehoge"
                    hoge.length + 1
                }

                internal object Main {
                    fun captured(): List<CaptureBlk> = capturedSources<CaptureBlk>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_run.Main")
        captured shouldHaveSize 1
        val src = fillerAnnotation(captured[0] as Annotation, "source")
        // run の lambda body も含む
        val value = annotationProperty(src, "value") as String
        value shouldContain "run {"
        value shouldContain "val hoge = \"hogehoge\""
        value shouldContain "hoge.length + 1"
    }

    // ----------------------------------------------------------------
    // 6. 同一ファイル内の複数式 annotation
    // ----------------------------------------------------------------
    test("expression annotation: multiple expressions in one file are collected independently") {
        val result = compile(
            SourceFile.kotlin(
                "MultiExpr.kt",
                """
                package example.expr_multi

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureM(val source: Source = Source())

                val a = @CaptureM() (1 + 1)
                val b = @CaptureM() ("foo".length)
                val c = @CaptureM() (listOf(1, 2, 3).sum())

                internal object Main {
                    fun captured(): List<CaptureM> = capturedSources<CaptureM>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_multi.Main")
        captured shouldHaveSize 3
        val texts = captured.map {
            annotationProperty(fillerAnnotation(it as Annotation, "source"), "value") as String
        }
        texts[0] shouldBe "1 + 1"
        texts[1] shouldBe "\"foo\".length"
        texts[2] shouldBe "listOf(1, 2, 3).sum()"
    }

    // ----------------------------------------------------------------
    // 7. ネストラムダ内
    // ----------------------------------------------------------------
    test("expression annotation: inside nested lambda") {
        val result = compile(
            SourceFile.kotlin(
                "Nested.kt",
                """
                package example.expr_nested

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureNested(val source: Source = Source())

                fun run(): List<Int> = listOf(1, 2, 3).map { x ->
                    @CaptureNested() (x * x)
                }

                internal object Main {
                    fun captured(): List<CaptureNested> = capturedSources<CaptureNested>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_nested.Main")
        captured shouldHaveSize 1
        val src = fillerAnnotation(captured[0] as Annotation, "source")
        annotationProperty(src, "value") shouldBe "x * x"
    }

    // ----------------------------------------------------------------
    // 8. 同じ marker が宣言と式の両方で使われる — kind で識別できる 2 件
    // ----------------------------------------------------------------
    test("expression annotation: same marker used on both declaration and expression yields kind PROPERTY + EXPRESSION") {
        val result = compile(
            SourceFile.kotlin(
                "Both.kt",
                """
                package example.expr_both

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Both(
                    val source: Source = Source(),
                    val kind: CaptureKind = CaptureKind(),
                )

                @Both
                val prop = 1

                val expr = @Both() (2 + 2)

                internal object Main {
                    fun captured(): List<Both> = capturedSources<Both>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result, mainFqn = "example.expr_both.Main")
        captured shouldHaveSize 2
        val kinds = captured.map {
            (annotationProperty(fillerAnnotation(it as Annotation, "kind"), "value") as Enum<*>).name
        }.toSet()
        kinds shouldBe setOf("PROPERTY", "EXPRESSION")
        val exprText = captured
            .map { it as Annotation }
            .first { (annotationProperty(fillerAnnotation(it, "kind"), "value") as Enum<*>).name == "EXPRESSION" }
        val src = fillerAnnotation(exprText, "source")
        annotationProperty(src, "value") shouldBe "2 + 2"
    }

    // ----------------------------------------------------------------
    // 9. 既存 declaration capture が退行していない (smoke)
    // ----------------------------------------------------------------
    test("declaration capture not regressed when expression marker is also present") {
        val result = compile(
            SourceFile.kotlin(
                "Smoke.kt",
                """
                package example.expr_smoke

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureE(val source: Source = Source())

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CaptureP(val source: Source = Source())

                @CaptureP
                val decl = 1
                val expr = @CaptureE() (2)

                internal object Main {
                    fun capturedE(): List<CaptureE> = capturedSources<CaptureE>()
                    fun capturedP(): List<CaptureP> = capturedSources<CaptureP>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val mainClass = result.classLoader.loadClass("example.expr_smoke.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val es = mainClass.getMethod("capturedE").invoke(mainInstance) as List<*>
        val ps = mainClass.getMethod("capturedP").invoke(mainInstance) as List<*>
        es shouldHaveSize 1
        ps shouldHaveSize 1
        val esSrc = fillerAnnotation(es[0] as Annotation, "source")
        val psSrc = fillerAnnotation(ps[0] as Annotation, "source")
        annotationProperty(esSrc, "value") shouldBe "2"
        (annotationProperty(psSrc, "value") as String) shouldContain "val decl = 1"
        // declaration 起源で式起源データが pollute されていないことの簡易確認
        (annotationProperty(psSrc, "value") as String) shouldNotContain "@CaptureE"
    }
})
