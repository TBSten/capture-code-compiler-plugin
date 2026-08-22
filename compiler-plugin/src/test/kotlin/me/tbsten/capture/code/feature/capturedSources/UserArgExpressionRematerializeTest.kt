package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * bug-004: expression 起源 (`@Marker(...) (expr)`) の marker user argument が silent に
 * default 値へ落ちる問題の修正を end-to-end (kctfork) で検証する。
 *
 * 修正内容 (`ConvertUserArgExpression` + `BuildUserArgPrimitive`):
 *
 * - 負数 literal (`-42L` / `-1.5`): K2 FIR が receiver literal への `unaryMinus()` として
 *   表現するため、 FIR 側で畳み込んで実値化
 * - `const val` 単純参照: `resolvedInitializer` の literal を畳み込んで実値化
 * - `::class` 参照: `IrClassReferenceShim` で `IrClassReference` を IR 再構築
 *   ([UserArgBoundaryValueTest] の C02 も参照)
 * - 配列 literal: `CompatContext.newIrVararg` で `IrVararg` を IR 再構築
 *   (旧挙動: **warning すら出ずに** default `[]`)
 * - nested annotation: `newIrConstructorCall` + `putCallValueArgument` で再帰的に IR 再構築
 * - 上記以外の複合定数式 (`BASE * 2 + 1` 等): default fallback は維持しつつ、
 *   実態に合った文面の `CC_USERARG_EXPRESSION_UNSUPPORTED` warning を発火
 *   (旧挙動: `Could not resolve enum entry 'kotlin.Int.plus'` という誤 warning)
 *
 * declaration 起源の挙動は IR deep copy 経路のため本修正の影響を受けない
 * (= 既存 [UserArgIrBuilderTest] / [UserArgBoundaryValueTest] が担保)。
 */
class UserArgExpressionRematerializeTest : FunSpec({

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

    test("負数 literal expr: -42L / -1.5 / -2.5f が default に落ちず実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "NegativeLiteralExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class NegativeExprMarker(
                    val l: Long = 0L,
                    val d: Double = 0.0,
                    val f: Float = 0.0f,
                    val source: Source = Source(),
                )

                val r0 = @NegativeExprMarker(l = -42L, d = -1.5, f = -2.5f) (1 + 2)

                internal object Main {
                    fun captured(): List<NegativeExprMarker> = capturedSources<NegativeExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        annotationProperty(captured[0] as Annotation, "l") shouldBe -42L
        annotationProperty(captured[0] as Annotation, "d") shouldBe -1.5
        annotationProperty(captured[0] as Annotation, "f") shouldBe -2.5f
        // 旧挙動の誤 warning (enum entry 扱い) が出ないことも確認する。
        result.messages shouldNotContain "Could not resolve enum entry"
    }

    test("const val 単純参照 expr: String / Int の const 値が畳み込まれて実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "ConstValRefExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal const val NAME = "Answer"
                internal const val BASE = 10

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ConstRefExprMarker(
                    val t: String = "",
                    val i: Int = 0,
                    val source: Source = Source(),
                )

                val r0 = @ConstRefExprMarker(t = NAME, i = BASE) (1 + 2)

                internal object Main {
                    fun captured(): List<ConstRefExprMarker> = capturedSources<ConstRefExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        annotationProperty(captured[0] as Annotation, "t") shouldBe "Answer"
        annotationProperty(captured[0] as Annotation, "i") shouldBe 10
        result.messages shouldNotContain "Could not resolve enum entry"
    }

    test("複合 const 式 expr: BASE * 2 + 1 は default に落ちつつ実態に合った warning が出る") {
        val result = compile(
            SourceFile.kotlin(
                "CompoundConstExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal const val BASE = 10

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class CompoundExprMarker(
                    val i: Int = 99,
                    val source: Source = Source(),
                )

                val r0 = @CompoundExprMarker(i = BASE * 2 + 1) (1 + 2)

                internal object Main {
                    fun captured(): List<CompoundExprMarker> = capturedSources<CompoundExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // 複合式の畳み込みは scope 外 (declaration 起源に移せば verbatim 保持される)。
        // default fallback は維持しつつ、 warning 文面が実態 (unsupported constant expression)
        // に合っていることを assert する。
        annotationProperty(captured[0] as Annotation, "i") shouldBe 99
        result.messages shouldContain "is a constant expression that is not supported"
        result.messages shouldContain "BASE * 2 + 1"
        result.messages shouldNotContain "Could not resolve enum entry"
    }

    test("arrayOf 呼び出し expr: FIR が配列 literal に変換するため実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "ArrayOfCallExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ArrayOfExprMarker(
                    val tags: Array<String> = [],
                    val source: Source = Source(),
                )

                val r0 = @ArrayOfExprMarker(tags = arrayOf("x")) (1 + 2)

                internal object Main {
                    fun captured(): List<ArrayOfExprMarker> = capturedSources<ArrayOfExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // K2 FIR の annotation-arguments transformer が `arrayOf("x")` を配列 literal node に
        // 変換するため、 配列 literal 経路 (`UserArgValue.ArrayValue`) に乗って実値で入る。
        @Suppress("UNCHECKED_CAST")
        val tags = annotationProperty(captured[0] as Annotation, "tags") as Array<String>
        tags.toList() shouldBe listOf("x")
        result.messages shouldNotContain "Could not resolve enum entry"
    }

    test("配列 literal expr: Array<String> と IntArray が silent にならず実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "ArrayLiteralExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ArrayExprMarker(
                    val tags: Array<String> = [],
                    val nums: IntArray = [],
                    val source: Source = Source(),
                )

                val r0 = @ArrayExprMarker(tags = ["a", "b"], nums = [1, 2, 3]) (1 + 2)

                internal object Main {
                    fun captured(): List<ArrayExprMarker> = capturedSources<ArrayExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        @Suppress("UNCHECKED_CAST")
        val tags = annotationProperty(captured[0] as Annotation, "tags") as Array<String>
        tags.toList() shouldBe listOf("a", "b")
        val nums = annotationProperty(captured[0] as Annotation, "nums") as IntArray
        nums.toList() shouldBe listOf(1, 2, 3)
    }

    test("nested annotation expr: Meta(note = ...) が default に落ちず実値で入る") {
        val result = compile(
            SourceFile.kotlin(
                "NestedAnnotationExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal annotation class Meta(
                    val note: String = "",
                    val count: Int = 0,
                )

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class NestedExprMarker(
                    val meta: Meta = Meta(),
                    val source: Source = Source(),
                )

                val r0 = @NestedExprMarker(meta = Meta(note = "hello")) (1 + 2)

                internal object Main {
                    fun captured(): List<NestedExprMarker> = capturedSources<NestedExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val meta = annotationProperty(captured[0] as Annotation, "meta") as Annotation
        annotationProperty(meta, "note") shouldBe "hello"
        // 指定しなかった nested parameter は annotation class 側の default が使われる。
        annotationProperty(meta, "count") shouldBe 0
        result.messages shouldNotContain "Could not resolve enum entry"
    }

    test("enum entry expr: 既存動作の regression がない (実値で入り enum warning も出ない)") {
        val result = compile(
            SourceFile.kotlin(
                "EnumRegressionExpr.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.EXPRESSION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LevelExprMarker(
                    val level: Level = Level.LOW,
                    val source: Source = Source(),
                ) {
                    enum class Level { LOW, HIGH }
                }

                val r0 = @LevelExprMarker(level = LevelExprMarker.Level.HIGH) (1 + 2)

                internal object Main {
                    fun captured(): List<LevelExprMarker> = capturedSources<LevelExprMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        (annotationProperty(captured[0] as Annotation, "level") as Enum<*>).name shouldBe "HIGH"
        result.messages shouldNotContain "Could not resolve enum entry"
    }
})
