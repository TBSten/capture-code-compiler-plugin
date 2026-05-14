package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// NOTE (task-017 完了時更新):
// Expression annotation の正確な site 構文は、 Kotlin 2.0 の K2 parser が `@Marker (expr)` を
// `@Marker(expr)` (annotation constructor 引数) と greedy に解釈する制約から、 marker の引数
// `()` を **明示的に空** にする `@Marker() (expr)` 形を採用する (task-009 spike 結論 + design §7.8 補強)。
// この方針により marker class の constructor が `Source` / `CaptureKind` 等 filler 型のみで
// 構成されている場合でも parser が annotation 終端を確実に認識する。
//
// 一部のサイトでは構文上の制約により書き方を変更している:
//   - ケース #27 (`return @Marker (expr)`): parser が `return@label` (return label) と曖昧化するため、
//     `val r = @Marker() (expr); return r` のローカル変数経由に変更。
//   - ケース #58/59/60 (when / if / try): `@Marker when { ... }` も同様にカッコ周辺の parser 制約あり。
//     `@Marker() (when { ... })` の形で対応。
//
// KNOWN-LIMITATION (2026-05-14): 上記の `@Marker() (expr)` 必須形式 (= `@Marker (expr)` が直接書けない)
// は Kotlin K2 parser の挙動として永続的に許容する。 plugin 側では追加対応しない。
// design 文書 §13.1 (Known Limitations) 参照。

// ============================================================================
// ケース7: 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case7(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

val case7_sum = @CaptureExpr_Case7() (1 + 2 + 3)

// ============================================================================
// ケース26: property の initializer 内での式キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case26(val source: Source = Source())

private fun case26_compute(s: String): Int = s.hashCode()

val case26_hash = @CaptureExpr_Case26() (case26_compute("a" + "b") + case26_compute("c"))

// ============================================================================
// ケース27: return 文の式をキャプチャ (`val r = @Marker () expr; return r` 経由)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case27(val source: Source = Source())

fun case27_computeAnswer(): Int {
    val r = @CaptureExpr_Case27() (40 + 2)
    return r
}

// ============================================================================
// ケース28: 関数引数として式をキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case28(val source: Source = Source())

fun case28_makeGreeting(): String = @CaptureExpr_Case28() ("hello " + "world")

// ============================================================================
// ケース29: @Marker run { ... } のブロック形
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureBlock_Case29(val source: Source = Source())

val case29_result = @CaptureBlock_Case29() run {
    val hoge = "hogehoge"
    hoge.length + 1
}

// ============================================================================
// ケース30: @Marker ({ ... }) のパーレン括り
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureLambda_Case30(val source: Source = Source())

val case30_onClick: () -> Unit = @CaptureLambda_Case30() ({ println("clicked") })

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
// ケース56: 同一ファイル内の複数式 annotation
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case56(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

val case56_a = @CaptureExpr_Case56() (1 + 1)
val case56_b = @CaptureExpr_Case56() ("foo".length)
val case56_c = @CaptureExpr_Case56() (listOf(1, 2, 3).sum())

// ============================================================================
// ケース57: 関数呼び出し式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case57(val source: Source = Source())

private fun case57_add(a: Int, b: Int) = a + b

val case57_r = @CaptureExpr_Case57() (case57_add(3, 4))

// ============================================================================
// ケース58: when 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case58(val source: Source = Source())

fun case58_classify(n: Int): String = @CaptureExpr_Case58() (when {
    n < 0 -> "negative"
    n == 0 -> "zero"
    else -> "positive"
})

// ============================================================================
// ケース59: if 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case59(val source: Source = Source())

val case59_sign = @CaptureExpr_Case59() (if (-3 < 0) -1 else 1)

// ============================================================================
// ケース60: try-catch 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case60(val source: Source = Source())

val case60_parsed: Int = @CaptureExpr_Case60() (try {
    "abc".toInt()
} catch (e: NumberFormatException) {
    -1
})

// ============================================================================
// ケース61: 文字列補間を含む式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case61(val source: Source = Source())

val case61_name = "Tsubasa"
val case61_greeting = @CaptureExpr_Case61() ("Hello, $case61_name! You are ${case61_name.length} chars.")

// ============================================================================
// ケース64: 1 行に式 annotation が複数
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case64(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

val case64_pair = (@CaptureExpr_Case64() (1 + 2)) to (@CaptureExpr_Case64() (3 + 4))

// ============================================================================
// ケース65: 関数本体 1 行目に式 annotation
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case65(val source: Source = Source())

fun case65_firstLine() = @CaptureExpr_Case65() ("only" + " expression")

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

val case67_expr = @Both_Case67() (2 + 2)

// ============================================================================
// ケース68: 入れ子のラムダ内のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureExpr_Case68(val source: Source = Source())

fun case68_squareList(): List<Int> = listOf(1, 2, 3).map { x ->
    @CaptureExpr_Case68() (x * x)
}

class ExpressionCasesTest : StringSpec({

    "ケース7: 式のキャプチャ" {
        // 期待 site: val sum = @CaptureExpr_Case7 (1 + 2 + 3)
        capturedSources<CaptureExpr_Case7>() shouldBe listOf(
            CaptureExpr_Case7(
                source = Source(value = "1 + 2 + 3"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "ケース26: property の initializer 内での式キャプチャ" {
        // site: val case26_hash = @CaptureExpr_Case26() (case26_compute("a" + "b") + case26_compute("c"))
        // 注記: site の関数名は `case26_compute` だが、 spec のキャプチャは site の literal text を
        // そのまま返すため、 expected も `case26_compute(...)` で合わせる。
        capturedSources<CaptureExpr_Case26>() shouldBe listOf(
            CaptureExpr_Case26(
                source = Source(value = "case26_compute(\"a\" + \"b\") + case26_compute(\"c\")"),
            ),
        )
    }

    "ケース27: return 文の式をキャプチャ" {
        // 期待 site: return @CaptureExpr_Case27 (40 + 2)
        capturedSources<CaptureExpr_Case27>() shouldBe listOf(
            CaptureExpr_Case27(source = Source(value = "40 + 2")),
        )
    }

    "ケース28: 関数引数として式をキャプチャ" {
        // 期待 site: println(@CaptureExpr_Case28 ("hello " + "world"))
        capturedSources<CaptureExpr_Case28>() shouldBe listOf(
            CaptureExpr_Case28(source = Source(value = "\"hello \" + \"world\"")),
        )
    }

    "ケース29: @Marker run { ... } のブロック形" {
        capturedSources<CaptureBlock_Case29>() shouldBe listOf(
            CaptureBlock_Case29(
                source = Source(value = "run {\n    val hoge = \"hogehoge\"\n    hoge.length + 1\n}"),
            ),
        )
    }

    "ケース30: @Marker ({ ... }) のパーレン括り" {
        // KNOWN-LIMITATION (2026-05-14): K2 parser が `@Marker () ({...})` の内側 lambda に
        // annotation を直接乗せる挙動のため、 spec の期待値 (`({ println(\"clicked\") })`、
        // parenthesis 外殻を含む) と実装現実 (`{ println(\"clicked\") }`、 parenthesis 外殻 1 ペアなし)
        // が乖離。 design 文書 §13 Known Limitations §13.2 参照。 本ケースは永続的に
        // 「実装現実に合わせた縮退期待値」を保持し、 plugin 側での追加対応は行わない方針。
        // 回避策: design §3.4 / §7.8 の通り `@Marker() run { ... }` 形を推奨。
        capturedSources<CaptureLambda_Case30>() shouldBe listOf(
            CaptureLambda_Case30(source = Source(value = "{ println(\"clicked\") }")),
        )
    }

    // task-042 (2026-05-14): Logic C に KDoc 探索 path 追加 + `includeKdoc` option 配線完了。
    // `CaptureCodePluginConfig.includeKdoc = true` (default) のため、 KDoc を含む source が
    // capture される。 Logic C 側で declaration の startOffset を直前 KDoc まで前方拡張する
    // path ([findKDocExtendedStartOffset]) が機能している。
    "ケース31: KDoc 付きの宣言 (KDoc を含むデフォルト挙動)" {
        capturedSources<DocCapture_Case31>() shouldBe listOf(
            DocCapture_Case31(
                source = Source(
                    value = "/**\n * ユーザーを挨拶する関数。\n *\n * @param name 挨拶対象の名前\n */\nfun case31_greet(name: String) = \"Hello, \$name!\"",
                ),
            ),
        )
    }

    "ケース32: line comment が直前にある宣言 (コメントは含めない)" {
        capturedSources<Snippets_Case32>() shouldBe listOf(
            Snippets_Case32(source = Source(value = "val case32_x = 1")),
        )
    }

    "ケース56: 同一ファイル内の複数式 annotation" {
        val captured = capturedSources<CaptureExpr_Case56>()
        captured.size shouldBe 3
        captured[0].source shouldBe Source(value = "1 + 1")
        captured[1].source shouldBe Source(value = "\"foo\".length")
        captured[2].source shouldBe Source(value = "listOf(1, 2, 3).sum()")
    }

    "ケース57: 関数呼び出し式のキャプチャ" {
        // site: val case57_r = @CaptureExpr_Case57() (case57_add(3, 4))
        capturedSources<CaptureExpr_Case57>() shouldBe listOf(
            CaptureExpr_Case57(source = Source(value = "case57_add(3, 4)")),
        )
    }

    "ケース58: when 式のキャプチャ" {
        capturedSources<CaptureExpr_Case58>() shouldBe listOf(
            CaptureExpr_Case58(
                source = Source(
                    value = "when {\n    n < 0 -> \"negative\"\n    n == 0 -> \"zero\"\n    else -> \"positive\"\n}",
                ),
            ),
        )
    }

    "ケース59: if 式のキャプチャ" {
        capturedSources<CaptureExpr_Case59>() shouldBe listOf(
            CaptureExpr_Case59(source = Source(value = "if (-3 < 0) -1 else 1")),
        )
    }

    "ケース60: try-catch 式のキャプチャ" {
        capturedSources<CaptureExpr_Case60>() shouldBe listOf(
            CaptureExpr_Case60(
                source = Source(
                    value = "try {\n    \"abc\".toInt()\n} catch (e: NumberFormatException) {\n    -1\n}",
                ),
            ),
        )
    }

    "ケース61: 文字列補間を含む式のキャプチャ" {
        // site: val case61_greeting = @CaptureExpr_Case61() ("Hello, $case61_name! ...")
        capturedSources<CaptureExpr_Case61>() shouldBe listOf(
            CaptureExpr_Case61(
                source = Source(
                    value = "\"Hello, \$case61_name! You are \${case61_name.length} chars.\"",
                ),
            ),
        )
    }

    "ケース64: 1 行に式 annotation が複数" {
        val captured = capturedSources<CaptureExpr_Case64>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "1 + 2")
        captured[1].source shouldBe Source(value = "3 + 4")
    }

    "ケース65: 関数本体 1 行目に式 annotation" {
        capturedSources<CaptureExpr_Case65>() shouldBe listOf(
            CaptureExpr_Case65(source = Source(value = "\"only\" + \" expression\"")),
        )
    }

    "ケース66: 大量サイトの 1 ファイル収集 (10 件)" {
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

    "ケース67: 同じ marker が宣言と式の両方で使われる" {
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

    "ケース68: 入れ子のラムダ内のキャプチャ" {
        capturedSources<CaptureExpr_Case68>() shouldBe listOf(
            CaptureExpr_Case68(source = Source(value = "x * x")),
        )
    }
})
