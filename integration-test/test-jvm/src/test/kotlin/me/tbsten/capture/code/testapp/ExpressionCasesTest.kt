package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// NOTE:
// Expression annotation の正確な site 構文 (`val x = @Marker (expr)`) は
// Kotlin 2.0 の parser では `@Marker(expr)` (annotation コンストラクタ) と
// 衝突するため、現状のテストでは marker class のみを宣言してテストロジック
// (期待値) を記述する。
// 実 site は compiler plugin 開発時に FIR/IR 段階で expression annotation を
// どう拾うかを決め、コンパイルが通る site 構文 (`@Marker { expr }` 風) を
// 確定したら site を追加する。
// 現状は全テストが `.config(enabled = false)` のためコンパイル可能であれば良い。

// ============================================================================
// ケース7: 式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case7(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

// ============================================================================
// ケース26: property の initializer 内での式キャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case26(val source: Source = Source())

// ============================================================================
// ケース27: return 文の式をキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case27(val source: Source = Source())

// ============================================================================
// ケース28: 関数引数として式をキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case28(val source: Source = Source())

// ============================================================================
// ケース29: @Marker run { ... } のブロック形 (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureBlock_Case29(val source: Source = Source())

// ============================================================================
// ケース30: @Marker ({ ... }) のパーレン括り (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureLambda_Case30(val source: Source = Source())

// ============================================================================
// ケース31: KDoc 付きの宣言 (FUNCTION annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DocCapture_Case31(val source: Source = Source())

/**
 * ユーザーを挨拶する関数。
 *
 * @param name 挨拶対象の名前
 */
@DocCapture_Case31
fun case31_greet(name: String) = "Hello, $name!"

// ============================================================================
// ケース32: line comment が直前にある宣言 (PROPERTY annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case32(val source: Source = Source())

// これは普通のコメント (KDoc ではない)
@Snippets_Case32
val case32_x = 1

// ============================================================================
// ケース56: 同一ファイル内の複数式 annotation (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case56(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

// ============================================================================
// ケース57: 関数呼び出し式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case57(val source: Source = Source())

// ============================================================================
// ケース58: when 式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case58(val source: Source = Source())

// ============================================================================
// ケース59: if 式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case59(val source: Source = Source())

// ============================================================================
// ケース60: try-catch 式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case60(val source: Source = Source())

// ============================================================================
// ケース61: 文字列補間を含む式のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case61(val source: Source = Source())

// ============================================================================
// ケース64: 1 行に式 annotation が複数 (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case64(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

// ============================================================================
// ケース65: 関数本体 1 行目に式 annotation (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case65(val source: Source = Source())

// ============================================================================
// ケース66: 大量サイトの 1 ファイル収集 (10 件 / PROPERTY annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class N_Case66(val source: Source = Source())

@N_Case66
val case66_n01 = 1

@N_Case66
val case66_n02 = 2

@N_Case66
val case66_n03 = 3

@N_Case66
val case66_n04 = 4

@N_Case66
val case66_n05 = 5

@N_Case66
val case66_n06 = 6

@N_Case66
val case66_n07 = 7

@N_Case66
val case66_n08 = 8

@N_Case66
val case66_n09 = 9

@N_Case66
val case66_n10 = 10

// ============================================================================
// ケース67: 同じ marker が宣言と式の両方で使われる (PROPERTY site のみ追加)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Both_Case67(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Both_Case67
val case67_prop = 1

// ============================================================================
// ケース68: 入れ子のラムダ内のキャプチャ (marker のみ宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case68(val source: Source = Source())

class ExpressionCasesTest : StringSpec({

    "ケース7: 式のキャプチャ".config(enabled = false) {
        // 期待 site: val sum = @CaptureExpr_Case7 (1 + 2 + 3)
        capturedSources<CaptureExpr_Case7>() shouldBe listOf(
            CaptureExpr_Case7(
                source = Source(value = "1 + 2 + 3"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "ケース26: property の initializer 内での式キャプチャ".config(enabled = false) {
        // 期待 site: val hash = @CaptureExpr_Case26 (compute("a" + "b") + compute("c"))
        capturedSources<CaptureExpr_Case26>() shouldBe listOf(
            CaptureExpr_Case26(
                source = Source(value = "compute(\"a\" + \"b\") + compute(\"c\")"),
            ),
        )
    }

    "ケース27: return 文の式をキャプチャ".config(enabled = false) {
        // 期待 site: return @CaptureExpr_Case27 (40 + 2)
        capturedSources<CaptureExpr_Case27>() shouldBe listOf(
            CaptureExpr_Case27(source = Source(value = "40 + 2")),
        )
    }

    "ケース28: 関数引数として式をキャプチャ".config(enabled = false) {
        // 期待 site: println(@CaptureExpr_Case28 ("hello " + "world"))
        capturedSources<CaptureExpr_Case28>() shouldBe listOf(
            CaptureExpr_Case28(source = Source(value = "\"hello \" + \"world\"")),
        )
    }

    "ケース29: @Marker run { ... } のブロック形".config(enabled = false) {
        capturedSources<CaptureBlock_Case29>() shouldBe listOf(
            CaptureBlock_Case29(
                source = Source(value = "run {\n    val hoge = \"hogehoge\"\n    hoge.length + 1\n}"),
            ),
        )
    }

    "ケース30: @Marker ({ ... }) のパーレン括り".config(enabled = false) {
        capturedSources<CaptureLambda_Case30>() shouldBe listOf(
            CaptureLambda_Case30(source = Source(value = "({ println(\"clicked\") })")),
        )
    }

    "ケース31: KDoc 付きの宣言 (KDoc を含むデフォルト挙動)".config(enabled = false) {
        capturedSources<DocCapture_Case31>() shouldBe listOf(
            DocCapture_Case31(
                source = Source(
                    value = "/**\n * ユーザーを挨拶する関数。\n *\n * @param name 挨拶対象の名前\n */\nfun case31_greet(name: String) = \"Hello, \$name!\"",
                ),
            ),
        )
    }

    "ケース32: line comment が直前にある宣言 (コメントは含めない)".config(enabled = false) {
        capturedSources<Snippets_Case32>() shouldBe listOf(
            Snippets_Case32(source = Source(value = "val case32_x = 1")),
        )
    }

    "ケース56: 同一ファイル内の複数式 annotation".config(enabled = false) {
        val captured = capturedSources<CaptureExpr_Case56>()
        captured.size shouldBe 3
        captured[0].source shouldBe Source(value = "1 + 1")
        captured[1].source shouldBe Source(value = "\"foo\".length")
        captured[2].source shouldBe Source(value = "listOf(1, 2, 3).sum()")
    }

    "ケース57: 関数呼び出し式のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case57>() shouldBe listOf(
            CaptureExpr_Case57(source = Source(value = "add(3, 4)")),
        )
    }

    "ケース58: when 式のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case58>() shouldBe listOf(
            CaptureExpr_Case58(
                source = Source(
                    value = "when {\n    n < 0 -> \"negative\"\n    n == 0 -> \"zero\"\n    else -> \"positive\"\n}",
                ),
            ),
        )
    }

    "ケース59: if 式のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case59>() shouldBe listOf(
            CaptureExpr_Case59(source = Source(value = "if (-3 < 0) -1 else 1")),
        )
    }

    "ケース60: try-catch 式のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case60>() shouldBe listOf(
            CaptureExpr_Case60(
                source = Source(
                    value = "try {\n    \"abc\".toInt()\n} catch (e: NumberFormatException) {\n    -1\n}",
                ),
            ),
        )
    }

    "ケース61: 文字列補間を含む式のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case61>() shouldBe listOf(
            CaptureExpr_Case61(
                source = Source(value = "\"Hello, \$name! You are \${name.length} chars.\""),
            ),
        )
    }

    "ケース64: 1 行に式 annotation が複数".config(enabled = false) {
        val captured = capturedSources<CaptureExpr_Case64>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "1 + 2")
        captured[1].source shouldBe Source(value = "3 + 4")
    }

    "ケース65: 関数本体 1 行目に式 annotation".config(enabled = false) {
        capturedSources<CaptureExpr_Case65>() shouldBe listOf(
            CaptureExpr_Case65(source = Source(value = "\"only\" + \" expression\"")),
        )
    }

    "ケース66: 大量サイトの 1 ファイル収集 (10 件)".config(enabled = false) {
        capturedSources<N_Case66>() shouldBe listOf(
            N_Case66(source = Source(value = "val case66_n01 = 1")),
            N_Case66(source = Source(value = "val case66_n02 = 2")),
            N_Case66(source = Source(value = "val case66_n03 = 3")),
            N_Case66(source = Source(value = "val case66_n04 = 4")),
            N_Case66(source = Source(value = "val case66_n05 = 5")),
            N_Case66(source = Source(value = "val case66_n06 = 6")),
            N_Case66(source = Source(value = "val case66_n07 = 7")),
            N_Case66(source = Source(value = "val case66_n08 = 8")),
            N_Case66(source = Source(value = "val case66_n09 = 9")),
            N_Case66(source = Source(value = "val case66_n10 = 10")),
        )
    }

    "ケース67: 同じ marker が宣言と式の両方で使われる".config(enabled = false) {
        capturedSources<Both_Case67>() shouldBe listOf(
            Both_Case67(
                source = Source(value = "val case67_prop = 1"),
                kind = CaptureKind(value = CaptureKind.Kind.PROPERTY),
            ),
            Both_Case67(
                source = Source(value = "2 + 2"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "ケース68: 入れ子のラムダ内のキャプチャ".config(enabled = false) {
        capturedSources<CaptureExpr_Case68>() shouldBe listOf(
            CaptureExpr_Case68(source = Source(value = "x * x")),
        )
    }
})
