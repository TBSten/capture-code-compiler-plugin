package me.tbsten.capture.code.spike

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 式 annotation `@Marker (expr)` が FIR / IR phase でどう扱われるかを実機検証する spike。
 *
 * 本テストは「assertion で挙動を縛る」のではなく、「kctfork で実機 compile して何が観測できるかを
 * `.local/tmp/expression-annotation-spike-<date>.md` に記録する」ことが目的。
 *
 * 観察項目 (a)-(g):
 * - (a) FIR phase で `FirStatement.annotations` に式 annotation が乗るか
 * - (b) IR phase で `IrExpression.annotations` (経由 [org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer]) に
 *       式 annotation が残るか
 * - (c) annotation の startOffset / endOffset がカッコ内側か外側か
 * - (d) `IrFileEntry.getSourceRangeInfo` の戻り (= 行列情報のみ。生テキストは [org.jetbrains.kotlin.ir.PsiIrFileEntry] 経由)
 * - (e) annotation が IR に残らない場合の FIR session storage 経由の代替経路の必要性
 * - (f) `@Marker run { ... }` / `@Marker ({ ... })` の挙動差
 * - (g) 1 行に式 annotation が複数あるケース
 *
 * R3 (LightTree モード) は本 spike では正攻法での切り替え API が compile 環境から制御し辛いため、
 * `useLightTree` フラグ相当の確認は別途 follow-up とし、本 spike では PSI 経路がメインの確認となる。
 * (kctfork は default で K2 + PSI mode で compile する。)
 */
class ExpressionAnnotationSpikeTest : FunSpec({

    // working directory: 通常 :compiler-plugin の project root か root project になる。
    // 確実に repo root の .local/tmp に出力するため、両方の candidate を試す。
    val outDir: File = run {
        val candidates = listOf(File(".local/tmp"), File("../.local/tmp"))
        val chosen = candidates.firstOrNull { it.parentFile?.exists() == true }
            ?: candidates.first()
        chosen.apply { mkdirs() }
    }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    val outputFile = File(outDir, "expression-annotation-spike-$date.md")
    val accumulator = StringBuilder().apply {
        appendLine("# spike: 式 annotation の FIR / IR 残存性 + offset 挙動")
        appendLine()
        appendLine("生成日: ${LocalDate.now()}")
        appendLine("Kotlin: 2.0.0 (kotlin-compiler-embeddable)")
        appendLine()
        appendLine("> 観察結果のみを記録する。assertion はあえて緩い (compile が通れば pass)。")
        appendLine("> 本 spike の結論は末尾「結論」セクションを参照。")
        appendLine()
    }

    fun runCase(name: String, markerFqns: Set<String>, sources: List<SourceFile>) {
        val report = SpikeReport(caseName = name)
        SpikeReportHolder.current = report
        SpikeReportHolder.markerFqns = markerFqns
        val result = try {
            KotlinCompilation().apply {
                this.sources = sources
                compilerPluginRegistrars = listOf(SpikePluginRegistrar(report, markerFqns))
                inheritClassPath = true
                jvmTarget = "17"
                messageOutputStream = System.out
            }.compile()
        } finally {
            SpikeReportHolder.current = null
            SpikeReportHolder.markerFqns = emptySet()
        }
        accumulator.appendLine(report.toMarkdown())
        accumulator.appendLine("- compile exitCode: `${result.exitCode}`")
        accumulator.appendLine()
    }

    // ---------------------------------------------------------------
    // ケース A: 最小式 annotation (ticket 検証項目 a/b/c/d)
    // `val sum = @CaptureExpr (1 + 2)` を compile し、annotation の残存と offset を観察
    // ---------------------------------------------------------------
    test("case-A minimal expression annotation: @CaptureExpr (1 + 2)") {
        runCase(
            name = "A: minimal-expression-annotation",
            markerFqns = setOf("spike.CaptureExpr"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    annotation class Source(val value: String = "")

                    @Target(AnnotationTarget.EXPRESSION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureExpr(val source: Source = Source())
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    val sum = @CaptureExpr (1 + 2)

                    fun main() {
                        println(sum)
                    }
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // ケース B: declaration annotation (比較用 baseline; (a)(b) の declaration 側裏付け)
    // `@CaptureProp val x = 1` の annotation が IR に残ることを確認 (既知の前提だが文書化)
    // ---------------------------------------------------------------
    test("case-B declaration annotation (baseline)") {
        runCase(
            name = "B: declaration-annotation-baseline",
            markerFqns = setOf("spike.CaptureProp"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    @Target(AnnotationTarget.PROPERTY)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureProp
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    @CaptureProp
                    val x = 1
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // ケース C: ブロック形 `@CaptureBlock run { ... }` (ticket 検証項目 f)
    // ---------------------------------------------------------------
    test("case-C @CaptureBlock run { ... } block form") {
        runCase(
            name = "C: block-form-run",
            markerFqns = setOf("spike.CaptureBlock"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    annotation class Source(val value: String = "")

                    @Target(AnnotationTarget.EXPRESSION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureBlock(val source: Source = Source())
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    val result = @CaptureBlock run {
                        val hoge = "hogehoge"
                        hoge.length + 1
                    }
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // ケース D: パーレン括り `@CaptureLambda ({ ... })` (ticket 検証項目 f)
    // ---------------------------------------------------------------
    test("case-D @CaptureLambda ({ ... }) paren form") {
        runCase(
            name = "D: paren-form-lambda",
            markerFqns = setOf("spike.CaptureLambda"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    annotation class Source(val value: String = "")

                    @Target(AnnotationTarget.EXPRESSION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureLambda(val source: Source = Source())
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    val onClick: () -> Unit = @CaptureLambda ({ println("clicked") })
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // ケース E: 1 行に複数の式 annotation (ticket 検証項目 g)
    // ---------------------------------------------------------------
    test("case-E multiple expression annotations on the same line") {
        runCase(
            name = "E: multiple-on-single-line",
            markerFqns = setOf("spike.CaptureExpr"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    annotation class Source(val value: String = "")

                    @Target(AnnotationTarget.EXPRESSION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureExpr(val source: Source = Source())
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    val pair = (@CaptureExpr (1 + 2)) to (@CaptureExpr (3 + 4))
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // ケース F: 入れ子ラムダ内の式 annotation (検証項目 g/f)
    // ---------------------------------------------------------------
    test("case-F expression annotation inside nested lambda") {
        runCase(
            name = "F: nested-lambda",
            markerFqns = setOf("spike.CaptureExpr"),
            sources = listOf(
                SourceFile.kotlin(
                    "Markers.kt",
                    """
                    package spike

                    annotation class Source(val value: String = "")

                    @Target(AnnotationTarget.EXPRESSION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class CaptureExpr(val source: Source = Source())
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Usage.kt",
                    """
                    package spike

                    fun main() {
                        val mapped = listOf(1, 2, 3).map { x ->
                            @CaptureExpr (x * x)
                        }
                        println(mapped)
                    }
                    """.trimIndent(),
                ),
            ),
        )
    }

    // ---------------------------------------------------------------
    // 全ケース実行後に観察結果を出力する。
    // 個別 test の AfterSpec で書く方法もあるが、追記しやすさを優先して
    // 全 test が終わったあとに同期的に書き出す。
    // ---------------------------------------------------------------
    afterSpec {
        accumulator.appendLine("---")
        accumulator.appendLine()
        accumulator.appendLine("## 結論 (本 spike の自動生成サマリ)")
        accumulator.appendLine()
        accumulator.appendLine("- 観察項目 (a)-(g) の生データは上記各ケースを参照。")
        accumulator.appendLine("- expression annotation の設計方針は design 文書 §5 Logic B-fir を参照。")
        outputFile.writeText(accumulator.toString())
        println("[spike] wrote observation log to ${outputFile.absolutePath}")
    }
})
