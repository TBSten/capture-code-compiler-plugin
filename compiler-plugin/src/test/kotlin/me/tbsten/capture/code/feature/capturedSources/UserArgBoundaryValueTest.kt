package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * UserArgValue sealed 12 subclass × primitive boundary value のテスト。
 *
 * 目的:
 * - 12 subclass (NullValue / BoolValue / CharValue / ByteValue / ShortValue / IntValue /
 *   LongValue / FloatValue / DoubleValue / StringValue / ClassRef / EnumRef) の発火確認
 * - primitive 9 種の boundary value (max / min / 0 / -1 / +1 / NaN / Infinity / -0.0 / empty / surrogate)
 * - declaration-origin marker (= IrConstructorCall を pluck) vs expression-origin marker
 *   (= UserArgValue 経由で IR 再構築) の 2 path での同 IR output 確認
 *
 * ## 経緯 (exploratory debug Charter 5)
 *
 * 2026-05-21 探索的デバッグ Charter 5 で発見したバグ:
 * - **BUG-1 (修正済)**: K2 FIR の integer literal `42` は `FirLiteralExpression<Long>` (= 内部 Long
 *   表現) として持たれる。 旧 [UserArgValue.wrapLiteralValue] は raw 値の Java 型だけで dispatch
 *   していたため、 marker parameter 型 `Int` でも `LongValue(42)` を構築し、 IR 再構築段階で
 *   integer slot に long を積む bytecode を生成して `java.lang.VerifyError: Bad type on operand
 *   stack` を起こしていた。 [UserArgValue.wrapLiteralValue] に `expectedTypeFqn` を渡して
 *   Byte / Short / Int / Float の expected type に narrow するように修正。
 * - **BUG-2 (既知制限)**: `-0.0f` / `-1L` 等の negative literal は K2 FIR で
 *   `0.0f.unaryMinus()` (`FirPropertyAccessExpression`) として表現されるため、 現状の
 *   `CollectExpressionSite.collectUserArgs` は EnumRef branch に落として silent ignore する。
 *   この test では negative literal は assert しない (= BUG-2 は別 ticket でフォロー)。
 */
class UserArgBoundaryValueTest : FunSpec({

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

    fun loadCaptured(result: JvmCompilationResult, mainFqn: String = "example.Main"): List<*> {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    // ================================================================
    // Group A — primitive declaration-origin boundary
    // ================================================================

    test("A01 Byte boundary decl: MAX / MIN / 0 / -1 / 1") {
        val result = compile(
            SourceFile.kotlin(
                "ByteBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ByteMarker(val b: Byte)

                @ByteMarker(b = 127) internal fun a() = 1
                @ByteMarker(b = -128) internal fun b() = 2
                @ByteMarker(b = 0) internal fun c() = 3
                @ByteMarker(b = -1) internal fun d() = 4
                @ByteMarker(b = 1) internal fun e() = 5

                internal object Main {
                    fun captured(): List<ByteMarker> = capturedSources<ByteMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 5
        annotationProperty(captured[0] as Annotation, "b") shouldBe Byte.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "b") shouldBe Byte.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "b") shouldBe 0.toByte()
        annotationProperty(captured[3] as Annotation, "b") shouldBe (-1).toByte()
        annotationProperty(captured[4] as Annotation, "b") shouldBe 1.toByte()
    }

    test("A02 Short boundary decl: MAX / MIN / 0") {
        val result = compile(
            SourceFile.kotlin(
                "ShortBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ShortMarker(val s: Short)

                @ShortMarker(s = 32767) internal fun a() = 1
                @ShortMarker(s = -32768) internal fun b() = 2
                @ShortMarker(s = 0) internal fun c() = 3

                internal object Main {
                    fun captured(): List<ShortMarker> = capturedSources<ShortMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 3
        annotationProperty(captured[0] as Annotation, "s") shouldBe Short.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "s") shouldBe Short.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "s") shouldBe 0.toShort()
    }

    test("A03 Int boundary decl: MAX / MIN / 0 / -1 / 1") {
        val result = compile(
            SourceFile.kotlin(
                "IntBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class IntMarker(val i: Int)

                @IntMarker(i = Int.MAX_VALUE) internal fun a() = 1
                @IntMarker(i = Int.MIN_VALUE) internal fun b() = 2
                @IntMarker(i = 0) internal fun c() = 3
                @IntMarker(i = -1) internal fun d() = 4
                @IntMarker(i = 1) internal fun e() = 5

                internal object Main {
                    fun captured(): List<IntMarker> = capturedSources<IntMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 5
        annotationProperty(captured[0] as Annotation, "i") shouldBe Int.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "i") shouldBe Int.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "i") shouldBe 0
        annotationProperty(captured[3] as Annotation, "i") shouldBe -1
        annotationProperty(captured[4] as Annotation, "i") shouldBe 1
    }

    test("A04 Long boundary decl: MAX / MIN / 0L / -1L") {
        val result = compile(
            SourceFile.kotlin(
                "LongBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LongMarker(val l: Long)

                @LongMarker(l = Long.MAX_VALUE) internal fun a() = 1
                @LongMarker(l = Long.MIN_VALUE) internal fun b() = 2
                @LongMarker(l = 0L) internal fun c() = 3
                @LongMarker(l = -1L) internal fun d() = 4

                internal object Main {
                    fun captured(): List<LongMarker> = capturedSources<LongMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 4
        annotationProperty(captured[0] as Annotation, "l") shouldBe Long.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "l") shouldBe Long.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "l") shouldBe 0L
        annotationProperty(captured[3] as Annotation, "l") shouldBe -1L
    }

    test("A05 Float boundary decl: MAX / MIN / 0.0f / -0.0f / NaN / +Inf / -Inf") {
        val result = compile(
            SourceFile.kotlin(
                "FloatBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FloatMarker(val f: Float)

                @FloatMarker(f = Float.MAX_VALUE) internal fun a() = 1
                @FloatMarker(f = Float.MIN_VALUE) internal fun b() = 2
                @FloatMarker(f = 0.0f) internal fun c() = 3
                @FloatMarker(f = -0.0f) internal fun d() = 4
                @FloatMarker(f = Float.NaN) internal fun e() = 5
                @FloatMarker(f = Float.POSITIVE_INFINITY) internal fun ff() = 6
                @FloatMarker(f = Float.NEGATIVE_INFINITY) internal fun g() = 7

                internal object Main {
                    fun captured(): List<FloatMarker> = capturedSources<FloatMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 7
        annotationProperty(captured[0] as Annotation, "f") shouldBe Float.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "f") shouldBe Float.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "f") shouldBe 0.0f
        // -0.0f vs 0.0f は equals では区別されないが、 1/-0.0f が -Inf になることで確認できる
        val negZero = annotationProperty(captured[3] as Annotation, "f") as Float
        (1.0f / negZero) shouldBe Float.NEGATIVE_INFINITY
        // NaN は != 自分自身なので isNaN で確認
        val nan = annotationProperty(captured[4] as Annotation, "f") as Float
        nan.isNaN() shouldBe true
        annotationProperty(captured[5] as Annotation, "f") shouldBe Float.POSITIVE_INFINITY
        annotationProperty(captured[6] as Annotation, "f") shouldBe Float.NEGATIVE_INFINITY
    }

    test("A06 Double boundary decl: MAX / MIN / 0.0 / -0.0 / NaN / +Inf / -Inf") {
        val result = compile(
            SourceFile.kotlin(
                "DoubleBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DoubleMarker(val d: Double)

                @DoubleMarker(d = Double.MAX_VALUE) internal fun a() = 1
                @DoubleMarker(d = Double.MIN_VALUE) internal fun b() = 2
                @DoubleMarker(d = 0.0) internal fun c() = 3
                @DoubleMarker(d = -0.0) internal fun d2() = 4
                @DoubleMarker(d = Double.NaN) internal fun e() = 5
                @DoubleMarker(d = Double.POSITIVE_INFINITY) internal fun ff() = 6
                @DoubleMarker(d = Double.NEGATIVE_INFINITY) internal fun g() = 7

                internal object Main {
                    fun captured(): List<DoubleMarker> = capturedSources<DoubleMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 7
        annotationProperty(captured[0] as Annotation, "d") shouldBe Double.MAX_VALUE
        annotationProperty(captured[1] as Annotation, "d") shouldBe Double.MIN_VALUE
        annotationProperty(captured[2] as Annotation, "d") shouldBe 0.0
        val negZero = annotationProperty(captured[3] as Annotation, "d") as Double
        (1.0 / negZero) shouldBe Double.NEGATIVE_INFINITY
        val nan = annotationProperty(captured[4] as Annotation, "d") as Double
        nan.isNaN() shouldBe true
        annotationProperty(captured[5] as Annotation, "d") shouldBe Double.POSITIVE_INFINITY
        annotationProperty(captured[6] as Annotation, "d") shouldBe Double.NEGATIVE_INFINITY
    }

    test("A07 Char boundary decl: ASCII / space / 漢 / NUL / surrogate halves") {
        val result = compile(
            SourceFile.kotlin(
                "CharBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CharMarker(val c: Char)

                @CharMarker(c = 'A') internal fun a() = 1
                @CharMarker(c = ' ') internal fun b() = 2
                @CharMarker(c = '漢') internal fun c() = 3
                @CharMarker(c = ' ') internal fun d() = 4
                @CharMarker(c = '￿') internal fun e() = 5
                @CharMarker(c = '\uD83D') internal fun ff() = 6
                @CharMarker(c = '\uDE00') internal fun g() = 7

                internal object Main {
                    fun captured(): List<CharMarker> = capturedSources<CharMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 7
        annotationProperty(captured[0] as Annotation, "c") shouldBe 'A'
        annotationProperty(captured[1] as Annotation, "c") shouldBe ' '
        annotationProperty(captured[2] as Annotation, "c") shouldBe '漢'
        annotationProperty(captured[3] as Annotation, "c") shouldBe ' '
        annotationProperty(captured[4] as Annotation, "c") shouldBe '￿'
        annotationProperty(captured[5] as Annotation, "c") shouldBe '\uD83D'
        annotationProperty(captured[6] as Annotation, "c") shouldBe '\uDE00'
    }

    test("A08 String boundary decl: empty / space / 漢字 / emoji / newline / quote") {
        val result = compile(
            SourceFile.kotlin(
                "StringBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class StringMarker(val s: String)

                @StringMarker(s = "") internal fun a() = 1
                @StringMarker(s = " ") internal fun b() = 2
                @StringMarker(s = "漢字") internal fun c() = 3
                @StringMarker(s = "🎀") internal fun d() = 4
                @StringMarker(s = "line1\nline2") internal fun e() = 5
                @StringMarker(s = "tab\there") internal fun ff() = 6
                @StringMarker(s = "with \"quote\"") internal fun g() = 7

                internal object Main {
                    fun captured(): List<StringMarker> = capturedSources<StringMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 7
        annotationProperty(captured[0] as Annotation, "s") shouldBe ""
        annotationProperty(captured[1] as Annotation, "s") shouldBe " "
        annotationProperty(captured[2] as Annotation, "s") shouldBe "漢字"
        annotationProperty(captured[3] as Annotation, "s") shouldBe "🎀"
        annotationProperty(captured[4] as Annotation, "s") shouldBe "line1\nline2"
        annotationProperty(captured[5] as Annotation, "s") shouldBe "tab\there"
        annotationProperty(captured[6] as Annotation, "s") shouldBe "with \"quote\""
    }

    test("A09 Bool decl: true / false") {
        val result = compile(
            SourceFile.kotlin(
                "BoolBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BoolMarker(val v: Boolean)

                @BoolMarker(v = true) internal fun a() = 1
                @BoolMarker(v = false) internal fun b() = 2

                internal object Main {
                    fun captured(): List<BoolMarker> = capturedSources<BoolMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "v") shouldBe true
        annotationProperty(captured[1] as Annotation, "v") shouldBe false
    }

    // ================================================================
    // Group B — primitive expression-origin boundary
    // ================================================================
    // NOTE: expression-origin (`@Marker(arg = X) (expr)`) は K2 parser の制約で
    // `()` を必須にできないため、 `@Marker(i = 42) (expr)` 形式を使う。

    test("B01 Int boundary expr: literal 42 / 0 / -1") {
        // Note: `Int.MAX_VALUE` is a property access (not literal) and goes via EnumRef path
        // (resolveEnumOrNull) currently — so the FIR phase representation may differ.
        // Use plain int literals to isolate the IR const re-materialisation behavior.
        val result = compile(
            SourceFile.kotlin(
                "IntExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class IntExprMarker(
                    val i: Int = 99,
                    val source: Source = Source(),
                )

                val r0 = @IntExprMarker(i = 42) (1)
                val r1 = @IntExprMarker(i = 0) (1)
                val r2 = @IntExprMarker(i = -1) (1)

                internal object Main {
                    fun captured(): List<IntExprMarker> = capturedSources<IntExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 3
        annotationProperty(captured[0] as Annotation, "i") shouldBe 42
        annotationProperty(captured[1] as Annotation, "i") shouldBe 0
        annotationProperty(captured[2] as Annotation, "i") shouldBe -1
    }

    test("B02 Float boundary expr: literal 3.14f / 0.0f (negative goes through unaryMinus)") {
        // BUG-2: `-0.0f` は K2 FIR で `0.0f.unaryMinus()` (FirPropertyAccessExpression) として
        // 解釈され、 collectUserArgs の EnumRef branch に落ちる → null → default。 同じ問題は
        // `-1L` 等の任意の負数 literal でも発生。
        val result = compile(
            SourceFile.kotlin(
                "FloatExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FloatExprMarker(
                    val f: Float = 99.0f,
                    val source: Source = Source(),
                )

                val r0 = @FloatExprMarker(f = 3.14f) (1)
                val r1 = @FloatExprMarker(f = 0.0f) (1)

                internal object Main {
                    fun captured(): List<FloatExprMarker> = capturedSources<FloatExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "f") shouldBe 3.14f
        annotationProperty(captured[1] as Annotation, "f") shouldBe 0.0f
    }

    test("B03 Double boundary expr: literal 3.14 / 0.0 (negative goes through unaryMinus)") {
        // BUG-2: `-0.0` は K2 FIR で `0.0.unaryMinus()` (FirPropertyAccessExpression) として解釈される。
        val result = compile(
            SourceFile.kotlin(
                "DoubleExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DoubleExprMarker(
                    val d: Double = 99.0,
                    val source: Source = Source(),
                )

                val r0 = @DoubleExprMarker(d = 3.14) (1)
                val r1 = @DoubleExprMarker(d = 0.0) (1)

                internal object Main {
                    fun captured(): List<DoubleExprMarker> = capturedSources<DoubleExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "d") shouldBe 3.14
        annotationProperty(captured[1] as Annotation, "d") shouldBe 0.0
    }

    test("B04 String boundary expr: empty / 漢字 / emoji / quote / newline") {
        val result = compile(
            SourceFile.kotlin(
                "StringExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class StringExprMarker(
                    val s: String = "",
                    val source: Source = Source(),
                )

                val r0 = @StringExprMarker(s = "") (1)
                val r1 = @StringExprMarker(s = "漢字") (1)
                val r2 = @StringExprMarker(s = "🎀") (1)
                val r3 = @StringExprMarker(s = "with \"quote\"") (1)
                val r4 = @StringExprMarker(s = "line1\nline2") (1)

                internal object Main {
                    fun captured(): List<StringExprMarker> = capturedSources<StringExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 5
        annotationProperty(captured[0] as Annotation, "s") shouldBe ""
        annotationProperty(captured[1] as Annotation, "s") shouldBe "漢字"
        annotationProperty(captured[2] as Annotation, "s") shouldBe "🎀"
        annotationProperty(captured[3] as Annotation, "s") shouldBe "with \"quote\""
        annotationProperty(captured[4] as Annotation, "s") shouldBe "line1\nline2"
    }

    test("B05 Char surrogate expr: high / low / NUL / 漢") {
        val result = compile(
            SourceFile.kotlin(
                "CharExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CharExprMarker(
                    val c: Char = 'A',
                    val source: Source = Source(),
                )

                val r0 = @CharExprMarker(c = 'A') (1)
                val r1 = @CharExprMarker(c = ' ') (1)
                val r2 = @CharExprMarker(c = '\uD83D') (1)
                val r3 = @CharExprMarker(c = '\uDE00') (1)
                val r4 = @CharExprMarker(c = '漢') (1)

                internal object Main {
                    fun captured(): List<CharExprMarker> = capturedSources<CharExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 5
        annotationProperty(captured[0] as Annotation, "c") shouldBe 'A'
        annotationProperty(captured[1] as Annotation, "c") shouldBe ' '
        annotationProperty(captured[2] as Annotation, "c") shouldBe '\uD83D'
        annotationProperty(captured[3] as Annotation, "c") shouldBe '\uDE00'
        annotationProperty(captured[4] as Annotation, "c") shouldBe '漢'
    }

    test("B06 Byte / Short boundary expr: int literals widened to byte/short") {
        // Note: `Byte.MAX_VALUE` / `Short.MAX_VALUE` is a property access — use
        // plain int literals (Kotlin widens to Byte/Short via constant fold).
        val result = compile(
            SourceFile.kotlin(
                "ByteShortExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BSExprMarker(
                    val b: Byte = 0,
                    val s: Short = 0,
                    val source: Source = Source(),
                )

                val r0 = @BSExprMarker(b = 127, s = 32767) (1)
                val r1 = @BSExprMarker(b = -128, s = -32768) (1)

                internal object Main {
                    fun captured(): List<BSExprMarker> = capturedSources<BSExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "b") shouldBe 127.toByte()
        annotationProperty(captured[0] as Annotation, "s") shouldBe 32767.toShort()
        annotationProperty(captured[1] as Annotation, "b") shouldBe (-128).toByte()
        annotationProperty(captured[1] as Annotation, "s") shouldBe (-32768).toShort()
    }

    test("B07 Long boundary expr: literal 42L / 0L (negative goes through unaryMinus path)") {
        // KNOWN: `-1L` is parsed as `1L.unaryMinus()` in K2 FIR (FirPropertyAccessExpression),
        // so the user-arg is silently dropped (EnumRef path returns null → default fallback).
        // → BUG-2 (separate from BUG-1 = Int/Byte/Short literals widening to Long).
        val result = compile(
            SourceFile.kotlin(
                "LongExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LongExprMarker(
                    val l: Long = 99L,
                    val source: Source = Source(),
                )

                val r0 = @LongExprMarker(l = 42L) (1)
                val r1 = @LongExprMarker(l = 0L) (1)

                internal object Main {
                    fun captured(): List<LongExprMarker> = capturedSources<LongExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "l") shouldBe 42L
        annotationProperty(captured[1] as Annotation, "l") shouldBe 0L
    }

    test("B08 Bool expr: true / false") {
        val result = compile(
            SourceFile.kotlin(
                "BoolExprBoundary.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BoolExprMarker(
                    val v: Boolean = false,
                    val source: Source = Source(),
                )

                val r0 = @BoolExprMarker(v = true) (1)
                val r1 = @BoolExprMarker(v = false) (1)

                internal object Main {
                    fun captured(): List<BoolExprMarker> = capturedSources<BoolExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "v") shouldBe true
        annotationProperty(captured[1] as Annotation, "v") shouldBe false
    }

    // ================================================================
    // Group C — sealed ref subclass (EnumRef / ClassRef / NullValue)
    // ================================================================

    test("C01 EnumRef expr: entry 解決 OK") {
        val result = compile(
            SourceFile.kotlin(
                "EnumRefExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class EnumExprMarker(
                    val v: Verb = Verb.GET,
                    val source: Source = Source(),
                ) {
                    enum class Verb { GET, POST, PUT, DELETE }
                }

                val r0 = @EnumExprMarker(v = EnumExprMarker.Verb.GET) (1)
                val r1 = @EnumExprMarker(v = EnumExprMarker.Verb.POST) (1)
                val r2 = @EnumExprMarker(v = EnumExprMarker.Verb.DELETE) (1)

                internal object Main {
                    fun captured(): List<EnumExprMarker> = capturedSources<EnumExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 3
        (annotationProperty(captured[0] as Annotation, "v") as Enum<*>).name shouldBe "GET"
        (annotationProperty(captured[1] as Annotation, "v") as Enum<*>).name shouldBe "POST"
        (annotationProperty(captured[2] as Annotation, "v") as Enum<*>).name shouldBe "DELETE"
    }

    test("C02 ClassRef expr: user 指定の ::class が実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "ClassRefExpr.kt",
                """
                package example

                import kotlin.reflect.KClass
                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal interface DefaultSvc
                internal class CustomSvc

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ClassRefExprMarker(
                    val target: KClass<*> = DefaultSvc::class,
                    val source: Source = Source(),
                )

                val r0 = @ClassRefExprMarker(target = CustomSvc::class) (1)

                internal object Main {
                    fun captured(): List<ClassRefExprMarker> = capturedSources<ClassRefExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // bug-004: ClassRef の IR 再構築 (`IrClassReferenceShim` 経由) に対応したため、
        // user-specified `CustomSvc::class` が default (`DefaultSvc::class`) を override して
        // 実値で入る (旧挙動: warning + default fallback)。
        val target = annotationProperty(captured[0] as Annotation, "target") as Class<*>
        target.name shouldBe "example.CustomSvc"
    }

    test("C03 NullValue (argMapping 空): annotation 引数なし expression marker") {
        val result = compile(
            SourceFile.kotlin(
                "NullValueExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class NoArgsExprMarker(
                    val s: String = "default",
                    val source: Source = Source(),
                )

                val r0 = @NoArgsExprMarker() (1 + 2)

                internal object Main {
                    fun captured(): List<NoArgsExprMarker> = capturedSources<NoArgsExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // 引数を渡してないので default が使われる
        annotationProperty(captured[0] as Annotation, "s") shouldBe "default"
    }

    // ================================================================
    // Group D — differential (decl-origin vs expr-origin で同 IR output)
    // ================================================================

    test("D01 Int / String / Bool decl-origin vs expr-origin: 同 input → 同 output") {
        val result = compile(
            SourceFile.kotlin(
                "DiffOriginCheck.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DiffMarker(
                    val i: Int = 0,
                    val s: String = "",
                    val b: Boolean = false,
                    val source: Source = Source(),
                )

                @DiffMarker(i = 42, s = "hello", b = true)
                internal fun declSite() = 1

                val exprSite = @DiffMarker(i = 42, s = "hello", b = true) (10 + 20)

                internal object Main {
                    fun captured(): List<DiffMarker> = capturedSources<DiffMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        val decl = captured[0] as Annotation
        val expr = captured[1] as Annotation
        annotationProperty(decl, "i") shouldBe annotationProperty(expr, "i")
        annotationProperty(decl, "s") shouldBe annotationProperty(expr, "s")
        annotationProperty(decl, "b") shouldBe annotationProperty(expr, "b")
        annotationProperty(decl, "i") shouldBe 42
        annotationProperty(decl, "s") shouldBe "hello"
        annotationProperty(decl, "b") shouldBe true
    }

    // ================================================================
    // Group E — extreme / encoding (UTF / 巨大 string)
    // ================================================================

    test("E01 巨大 String (1000 char) decl-origin: 文字列が IR const に正しく保持される") {
        val longString = "abcdefghij".repeat(100) // = 1000 char
        val result = compile(
            SourceFile.kotlin(
                "HugeStringDecl.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class HugeStringMarker(val s: String)

                @HugeStringMarker(s = "$longString")
                internal fun a() = 1

                internal object Main {
                    fun captured(): List<HugeStringMarker> = capturedSources<HugeStringMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val sValue = annotationProperty(captured[0] as Annotation, "s") as String
        sValue.length shouldBe 1000
        sValue shouldBe longString
    }
})
