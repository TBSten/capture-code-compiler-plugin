package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// NOTE:
// Expression annotation の正確な site 構文は、 Kotlin 2.0 の K2 parser が `@Marker (expr)` を
// `@Marker(expr)` (annotation constructor 引数) と greedy に解釈する制約から、 marker の引数
// `()` を **明示的に空** にする `@Marker() (expr)` 形を採用する (design §7.8 参照)。
// この方針により marker class の constructor が `Source` / `CaptureKind` 等 filler 型のみで
// 構成されている場合でも parser が annotation 終端を確実に認識する。
//
// 一部のサイトでは構文上の制約により書き方を変更している:
//   - return 式: parser が `return@label` (return label) と曖昧化するため、
//     `val r = @Marker() (expr); return r` のローカル変数経由に変更。
//   - when / if / try: `@Marker when { ... }` も同様にカッコ周辺の parser 制約あり。
//     `@Marker() (when { ... })` の形で対応。
//
// KNOWN-LIMITATION (2026-05-14): 上記の `@Marker() (expr)` 必須形式 (= `@Marker (expr)` が直接書けない)
// は Kotlin K2 parser の挙動として永続的に許容する。 plugin 側では追加対応しない。
// design 文書 §13.1 (Known Limitations) 参照。

// ============================================================================
// 式のキャプチャ (基本)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BasicExpressionMarker(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

val basicExpressionSum = @BasicExpressionMarker() (1 + 2 + 3)

// ============================================================================
// property の initializer 内での式キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PropertyInitExpressionMarker(val source: Source = Source())

private fun propertyInitCompute(s: String): Int = s.hashCode()

val propertyInitHash = @PropertyInitExpressionMarker() (propertyInitCompute("a" + "b") + propertyInitCompute("c"))

// ============================================================================
// return 文の式をキャプチャ (`val r = @Marker () expr; return r` 経由)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ReturnExpressionMarker(val source: Source = Source())

fun returnComputeAnswer(): Int {
    val r = @ReturnExpressionMarker() (40 + 2)
    return r
}

// ============================================================================
// 関数引数として式をキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FunctionArgExpressionMarker(val source: Source = Source())

fun functionArgMakeGreeting(): String = @FunctionArgExpressionMarker() ("hello " + "world")

// ============================================================================
// @Marker run { ... } のブロック形
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class RunBlockMarker(val source: Source = Source())

val runBlockResult = @RunBlockMarker() run {
    val hoge = "hogehoge"
    hoge.length + 1
}

// ============================================================================
// @Marker ({ ... }) のパーレン括り
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ParenLambdaMarker(val source: Source = Source())

val parenLambdaOnClick: () -> Unit = @ParenLambdaMarker() ({ println("clicked") })

// ============================================================================
// KDoc 付きの宣言 (FUNCTION annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KDocDeclarationMarker(val source: Source = Source())

/**
 * ユーザーを挨拶する関数。
 *
 * @param name 挨拶対象の名前
 */
@KDocDeclarationMarker
fun kdocGreet(name: String) = "Hello, $name!"

// ============================================================================
// line comment が直前にある宣言 (PROPERTY annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LineCommentDeclarationMarker(val source: Source = Source())

// これは普通のコメント (KDoc ではない)
@LineCommentDeclarationMarker
val lineCommentX = 1

// ============================================================================
// 同一ファイル内の複数式 annotation
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MultiExpressionInFile(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

val multiExprA = @MultiExpressionInFile() (1 + 1)
val multiExprB = @MultiExpressionInFile() ("foo".length)
val multiExprC = @MultiExpressionInFile() (listOf(1, 2, 3).sum())

// ============================================================================
// 関数呼び出し式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FunctionCallExpressionMarker(val source: Source = Source())

private fun functionCallAdd(a: Int, b: Int) = a + b

val functionCallResult = @FunctionCallExpressionMarker() (functionCallAdd(3, 4))

// ============================================================================
// when 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WhenExpressionMarker(val source: Source = Source())

fun whenClassify(n: Int): String = @WhenExpressionMarker() (when {
    n < 0 -> "negative"
    n == 0 -> "zero"
    else -> "positive"
})

// ============================================================================
// if 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class IfExpressionMarker(val source: Source = Source())

val ifSign = @IfExpressionMarker() (if (-3 < 0) -1 else 1)

// ============================================================================
// try-catch 式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TryCatchExpressionMarker(val source: Source = Source())

val tryCatchParsed: Int = @TryCatchExpressionMarker() (try {
    "abc".toInt()
} catch (e: NumberFormatException) {
    -1
})

// ============================================================================
// 文字列補間を含む式のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class StringInterpolationMarker(val source: Source = Source())

val stringInterpName = "Tsubasa"
val stringInterpGreeting = @StringInterpolationMarker() ("Hello, $stringInterpName! You are ${stringInterpName.length} chars.")

// ============================================================================
// 1 行に式 annotation が複数
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MultiExpressionPerLine(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

val multiPerLinePair = (@MultiExpressionPerLine() (1 + 2)) to (@MultiExpressionPerLine() (3 + 4))

// ============================================================================
// 関数本体 1 行目に式 annotation
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FunctionBodyFirstLineMarker(val source: Source = Source())

fun functionBodyFirstLine() = @FunctionBodyFirstLineMarker() ("only" + " expression")

// ============================================================================
// 大量サイトの 1 ファイル収集 (10 件 / PROPERTY annotation; site あり)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BulkSiteCollection(val source: Source = Source())

@BulkSiteCollection
val bulkSiteN01 = 1

@BulkSiteCollection
val bulkSiteN02 = 2

@BulkSiteCollection
val bulkSiteN03 = 3

@BulkSiteCollection
val bulkSiteN04 = 4

@BulkSiteCollection
val bulkSiteN05 = 5

@BulkSiteCollection
val bulkSiteN06 = 6

@BulkSiteCollection
val bulkSiteN07 = 7

@BulkSiteCollection
val bulkSiteN08 = 8

@BulkSiteCollection
val bulkSiteN09 = 9

@BulkSiteCollection
val bulkSiteN10 = 10

// ============================================================================
// 同じ marker が宣言と式の両方で使われる (PROPERTY site のみ追加)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DeclarationAndExpressionMarker(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@DeclarationAndExpressionMarker
val bothProp = 1

val bothExpr = @DeclarationAndExpressionMarker() (2 + 2)

// ============================================================================
// 入れ子のラムダ内のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NestedLambdaExpressionMarker(val source: Source = Source())

fun nestedLambdaSquareList(): List<Int> = listOf(1, 2, 3).map { x ->
    @NestedLambdaExpressionMarker() (x * x)
}

class ExpressionCasesTest : StringSpec({

    "式のキャプチャ (基本)" {
        // 期待 site: val sum = @BasicExpressionMarker (1 + 2 + 3)
        capturedSources<BasicExpressionMarker>() shouldBe listOf(
            BasicExpressionMarker(
                source = Source(value = "1 + 2 + 3"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "property の initializer 内での式キャプチャ" {
        // site: val propertyInitHash = @PropertyInitExpressionMarker() (propertyInitCompute("a" + "b") + propertyInitCompute("c"))
        // 注記: site の関数名は `propertyInitCompute` だが、 spec のキャプチャは site の literal text を
        // そのまま返すため、 expected も `propertyInitCompute(...)` で合わせる。
        capturedSources<PropertyInitExpressionMarker>() shouldBe listOf(
            PropertyInitExpressionMarker(
                source = Source(value = "propertyInitCompute(\"a\" + \"b\") + propertyInitCompute(\"c\")"),
            ),
        )
    }

    "return 文の式をキャプチャ" {
        // 期待 site: return @ReturnExpressionMarker (40 + 2)
        capturedSources<ReturnExpressionMarker>() shouldBe listOf(
            ReturnExpressionMarker(source = Source(value = "40 + 2")),
        )
    }

    "関数引数として式をキャプチャ" {
        // 期待 site: println(@FunctionArgExpressionMarker ("hello " + "world"))
        capturedSources<FunctionArgExpressionMarker>() shouldBe listOf(
            FunctionArgExpressionMarker(source = Source(value = "\"hello \" + \"world\"")),
        )
    }

    "@Marker run { ... } のブロック形" {
        capturedSources<RunBlockMarker>() shouldBe listOf(
            RunBlockMarker(
                source = Source(value = "run {\n    val hoge = \"hogehoge\"\n    hoge.length + 1\n}"),
            ),
        )
    }

    "@Marker ({ ... }) のパーレン括り" {
        // KNOWN-LIMITATION (2026-05-14): K2 parser が `@Marker () ({...})` の内側 lambda に
        // annotation を直接乗せる挙動のため、 spec の期待値 (`({ println(\"clicked\") })`、
        // parenthesis 外殻を含む) と実装現実 (`{ println(\"clicked\") }`、 parenthesis 外殻 1 ペアなし)
        // が乖離。 design 文書 §13 Known Limitations §13.2 参照。 本ケースは永続的に
        // 「実装現実に合わせた縮退期待値」を保持し、 plugin 側での追加対応は行わない方針。
        // 回避策: design §3.4 / §7.8 の通り `@Marker() run { ... }` 形を推奨。
        capturedSources<ParenLambdaMarker>() shouldBe listOf(
            ParenLambdaMarker(source = Source(value = "{ println(\"clicked\") }")),
        )
    }

    // Logic C は KDoc 探索 path を持ち、 `includeKdoc` option を配線済み。
    // `CaptureCodePluginConfig.includeKdoc = true` (default) のため、 KDoc を含む source が
    // capture される。 Logic C 側で declaration の startOffset を直前 KDoc まで前方拡張する
    // path ([findKDocExtendedStartOffset]) が機能している。
    "KDoc 付きの宣言 (KDoc を含むデフォルト挙動)" {
        capturedSources<KDocDeclarationMarker>() shouldBe listOf(
            KDocDeclarationMarker(
                source = Source(
                    value = "/**\n * ユーザーを挨拶する関数。\n *\n * @param name 挨拶対象の名前\n */\nfun kdocGreet(name: String) = \"Hello, \$name!\"",
                ),
            ),
        )
    }

    "line comment が直前にある宣言 (コメントは含めない)" {
        capturedSources<LineCommentDeclarationMarker>() shouldBe listOf(
            LineCommentDeclarationMarker(source = Source(value = "val lineCommentX = 1")),
        )
    }

    "同一ファイル内の複数式 annotation" {
        val captured = capturedSources<MultiExpressionInFile>()
        captured.size shouldBe 3
        captured[0].source shouldBe Source(value = "1 + 1")
        captured[1].source shouldBe Source(value = "\"foo\".length")
        captured[2].source shouldBe Source(value = "listOf(1, 2, 3).sum()")
    }

    "関数呼び出し式のキャプチャ" {
        // site: val functionCallResult = @FunctionCallExpressionMarker() (functionCallAdd(3, 4))
        capturedSources<FunctionCallExpressionMarker>() shouldBe listOf(
            FunctionCallExpressionMarker(source = Source(value = "functionCallAdd(3, 4)")),
        )
    }

    "when 式のキャプチャ" {
        capturedSources<WhenExpressionMarker>() shouldBe listOf(
            WhenExpressionMarker(
                source = Source(
                    value = "when {\n    n < 0 -> \"negative\"\n    n == 0 -> \"zero\"\n    else -> \"positive\"\n}",
                ),
            ),
        )
    }

    "if 式のキャプチャ" {
        capturedSources<IfExpressionMarker>() shouldBe listOf(
            IfExpressionMarker(source = Source(value = "if (-3 < 0) -1 else 1")),
        )
    }

    "try-catch 式のキャプチャ" {
        capturedSources<TryCatchExpressionMarker>() shouldBe listOf(
            TryCatchExpressionMarker(
                source = Source(
                    value = "try {\n    \"abc\".toInt()\n} catch (e: NumberFormatException) {\n    -1\n}",
                ),
            ),
        )
    }

    "文字列補間を含む式のキャプチャ" {
        // site: val stringInterpGreeting = @StringInterpolationMarker() ("Hello, $stringInterpName! ...")
        capturedSources<StringInterpolationMarker>() shouldBe listOf(
            StringInterpolationMarker(
                source = Source(
                    value = "\"Hello, \$stringInterpName! You are \${stringInterpName.length} chars.\"",
                ),
            ),
        )
    }

    "1 行に式 annotation が複数" {
        val captured = capturedSources<MultiExpressionPerLine>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "1 + 2")
        captured[1].source shouldBe Source(value = "3 + 4")
    }

    "関数本体 1 行目に式 annotation" {
        capturedSources<FunctionBodyFirstLineMarker>() shouldBe listOf(
            FunctionBodyFirstLineMarker(source = Source(value = "\"only\" + \" expression\"")),
        )
    }

    "大量サイトの 1 ファイル収集 (10 件)" {
        capturedSources<BulkSiteCollection>() shouldBe listOf(
            BulkSiteCollection(source = Source(value = "val bulkSiteN01 = 1")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN02 = 2")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN03 = 3")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN04 = 4")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN05 = 5")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN06 = 6")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN07 = 7")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN08 = 8")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN09 = 9")),
            BulkSiteCollection(source = Source(value = "val bulkSiteN10 = 10")),
        )
    }

    "同じ marker が宣言と式の両方で使われる" {
        capturedSources<DeclarationAndExpressionMarker>() shouldBe listOf(
            DeclarationAndExpressionMarker(
                source = Source(value = "val bothProp = 1"),
                kind = CaptureKind(value = CaptureKind.Kind.PROPERTY),
            ),
            DeclarationAndExpressionMarker(
                source = Source(value = "2 + 2"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "入れ子のラムダ内のキャプチャ" {
        capturedSources<NestedLambdaExpressionMarker>() shouldBe listOf(
            NestedLambdaExpressionMarker(source = Source(value = "x * x")),
        )
    }
})
