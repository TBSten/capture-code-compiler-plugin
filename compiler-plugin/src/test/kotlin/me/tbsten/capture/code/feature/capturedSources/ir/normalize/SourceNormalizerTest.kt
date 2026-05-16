package me.tbsten.capture.code.feature.capturedSources.ir.normalize

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [NormalizeSource] の挙動を網羅する unit test。
 *
 * design §5 Logic D に定義された正規化ステップ (dedent / blank trim / package-import strip /
 * leading annotation strip) を、入力 String → 出力 String の pure function として検証する。
 *
 * カバレッジ:
 * - dedent (インデント 0 / 2 / 4 / mixed)
 * - blank trim (前後 / 中間 / 全 blank)
 * - 結合 (dedent + blank trim)
 * - エッジ (1 行 / 改行なし / 改行のみ / 空文字列 / CRLF / 全角空白)
 * - declaration ケース (クラス内メンバ関数 / 多重ネスト / sealed class multi-line 抜粋)
 * - file 起源 (`stripPackageAndImport`)
 * - declaration 起源の保険 (`stripLeadingAnnotationLines`)
 * - idempotent (二度通しても同じ)
 */
class SourceNormalizerTest : StringSpec({

    // -----------------------------------------------------------------
    // dedent: インデント幅のバリエーション
    // -----------------------------------------------------------------
    "dedent: インデントが 0 ならそのまま" {
        val input = "val x = 1\nval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "dedent: 全行のインデントが 4 ならすべて 4 文字 dedent される" {
        val input = "    val x = 1\n    val y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "dedent: 全行のインデントが 2 ならすべて 2 文字 dedent される" {
        val input = "  val x = 1\n  val y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "dedent: mixed indent — 最小幅でそろう (4 / 8 → 0 / 4)" {
        val input = "    fun findById(id: Int): String? {\n        return null\n    }"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe
            "fun findById(id: Int): String? {\n    return null\n}"
    }

    "dedent: 多重ネストでも最小幅でそろう (12 / 16 → 0 / 4)" {
        // 多重ネストをモチーフ: class Outer { class Inner { class Deepest { @DeepSnippet fun deepFunc() = "deep" }}}
        val input = "            fun deepFunc() = \"deep\""
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "fun deepFunc() = \"deep\""
    }

    "dedent: 空白行はインデント計算から除外され、空白行は空文字列になる" {
        val input = "    val x = 1\n\n    val y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\n\nval y = 2"
    }

    "dedent: 全行 blank なら何もしない (空白行は空文字列に正規化される)" {
        val input = "    \n  \n"
        // 全行 blank → blank trim で 1 行も残らない
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe ""
    }

    "dedent: tab indent も dedent される" {
        val input = "\tval x = 1\n\tval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "dedent: minIndent より長い行 (= 余剰インデント) は余剰分が残る" {
        // 1 行目: 4 space + body, 2 行目: 8 space + body → minIndent = 4
        val input = "    val x = 1\n        val y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe
            "val x = 1\n    val y = 2"
    }

    "dedent: 1 行入力 (改行なし) は idempotent" {
        NormalizeSource()("val x = 1", NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1"
    }

    "dedent: 1 行 (改行なし) でも先頭インデントがあれば dedent" {
        NormalizeSource()("    val x = 1", NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1"
    }

    // -----------------------------------------------------------------
    // blank trim: 先頭末尾の空白行を drop
    // -----------------------------------------------------------------
    "blank trim: 先頭の空白行を drop" {
        val input = "\n\nval x = 1"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1"
    }

    "blank trim: 末尾の空白行を drop" {
        val input = "val x = 1\n\n"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1"
    }

    "blank trim: 中間の空白行は保持" {
        val input = "val x = 1\n\nval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\n\nval y = 2"
    }

    "blank trim: 先頭末尾の whitespace のみの行も drop" {
        val input = "   \nval x = 1\n   "
        // 先頭末尾の "   " はインデント計算後に空文字列となる -> blank trim で除去
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1"
    }

    "blank trim: 全行 blank なら空文字列が返る" {
        val input = "\n\n\n"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe ""
    }

    // -----------------------------------------------------------------
    // エッジケース
    // -----------------------------------------------------------------
    "edge: 空文字列はそのまま空文字列" {
        NormalizeSource()("", NormalizeOptions.DECLARATION_DEFAULT) shouldBe ""
    }

    "edge: 改行のみ (LF 1 文字) は空文字列に正規化される" {
        NormalizeSource()("\n", NormalizeOptions.DECLARATION_DEFAULT) shouldBe ""
    }

    "edge: CRLF は LF に正規化される" {
        val input = "val x = 1\r\nval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "edge: CR のみ (古い Mac OS) も LF に正規化される" {
        val input = "val x = 1\rval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "val x = 1\nval y = 2"
    }

    "edge: 全角空白は dedent 対象外 (= ASCII space/tab のみ)" {
        // 全角空白 + ASCII の混在: 全角空白は whitespace 幅にカウントされない
        // → 最小幅 0、dedent なし
        val input = "　val x = 1\nval y = 2"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "　val x = 1\nval y = 2"
    }

    // -----------------------------------------------------------------
    // idempotent: 既に正規化済みのテキストを通しても変わらない
    // -----------------------------------------------------------------
    "idempotent: 1 行宣言は何度通しても同じ" {
        val input = "val x = 1"
        val once = NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT)
        val twice = NormalizeSource()(once, NormalizeOptions.DECLARATION_DEFAULT)
        once shouldBe input
        twice shouldBe once
    }

    "idempotent: dedent された multi-line も再度通しても同じ" {
        val input = "fun findById(id: Int): String? {\n    return null\n}"
        val once = NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT)
        val twice = NormalizeSource()(once, NormalizeOptions.DECLARATION_DEFAULT)
        once shouldBe input
        twice shouldBe once
    }

    "idempotent: 中間 blank line を含む multi-line も再度通しても同じ" {
        val input = "val x = 1\n\nval y = 2"
        val once = NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT)
        val twice = NormalizeSource()(once, NormalizeOptions.DECLARATION_DEFAULT)
        once shouldBe input
        twice shouldBe once
    }

    // -----------------------------------------------------------------
    // 結合: dedent + blank trim
    // -----------------------------------------------------------------
    "combo: 先頭末尾空行 + dedent" {
        val input = "\n    fun findById(id: Int): String? {\n        return null\n    }\n"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT) shouldBe
            "fun findById(id: Int): String? {\n    return null\n}"
    }

    "combo: dedent を OFF にすればインデントが残る" {
        val input = "    val x = 1"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT.copy(dedent = false)) shouldBe
            "    val x = 1"
    }

    "combo: trimBlankEdges を OFF にすれば前後の空行が残る" {
        val input = "\nval x = 1\n"
        NormalizeSource()(input, NormalizeOptions.DECLARATION_DEFAULT.copy(trimBlankEdges = false)) shouldBe
            "\nval x = 1\n"
    }

    // -----------------------------------------------------------------
    // declaration 起源の代表ケース (クラス内メンバ / 多重ネスト / sealed class)
    // -----------------------------------------------------------------
    "declaration: クラス内のメンバ関数 dedent (IrFileEntry 範囲が `    fun ...` のとき)" {
        val raw = "    fun findById(id: Int): String? {\n        return null\n    }"
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe
            "fun findById(id: Int): String? {\n    return null\n}"
    }

    "declaration: 多重ネスト (Outer.Inner.Deepest 内 1 行 fun)" {
        val raw = "            fun deepFunc() = \"deep\""
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe "fun deepFunc() = \"deep\""
    }

    "declaration: sealed class とその子クラス (multi-line body) — interior 構造を維持" {
        val raw = """    sealed class Result {
        class Success(val value: String) : Result()
        class Failure(val error: String) : Result()
    }"""
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe """sealed class Result {
    class Success(val value: String) : Result()
    class Failure(val error: String) : Result()
}"""
    }

    "declaration: KDoc 付きの 1 行関数 (annotation 行は IR offset 範囲外 = 入力に含まれない)" {
        val raw = "/**\n * ユーザーを挨拶する関数。\n *\n * @param name 挨拶対象の名前\n */\nfun greet(name: String) = \"Hello, \$name!\""
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
    }

    "declaration: line comment は range 外 (入力に含まれない) → val x = 1 のみ" {
        val raw = "val x = 1"
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
    }

    // -----------------------------------------------------------------
    // file 起源: stripPackageAndImport
    // -----------------------------------------------------------------
    "file: package / import 行は除外され、宣言が残る" {
        val raw = """package com.example

import io.github.tbsten.capturecode.*

@CaptureCode
internal annotation class FileMarker"""
        NormalizeSource()(raw, NormalizeOptions.FILE_DEFAULT) shouldBe """@CaptureCode
internal annotation class FileMarker"""
    }

    "file: package が複数行モード (1 行ずつ) と blank 行を挟む形式も除外" {
        val raw = """package com.example

import a.b
import a.c

val x = 1"""
        NormalizeSource()(raw, NormalizeOptions.FILE_DEFAULT) shouldBe "val x = 1"
    }

    "file: stripPackageAndImport が OFF なら package / import 行は残る (declaration default)" {
        val raw = """package com.example

import a.b

val x = 1"""
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
    }

    "file: 中間に出現した import-like な行 (= 既に宣言の中に入ってからの import 文字列) は drop しない" {
        // 最初の non-package/import/blank 行に到達したら、それ以降は触らない
        val raw = """package com.example

val message = "import this string"
import a.b"""
        NormalizeSource()(raw, NormalizeOptions.FILE_DEFAULT) shouldBe """val message = "import this string"
import a.b"""
    }

    // -----------------------------------------------------------------
    // declaration 起源の保険: stripLeadingAnnotationLines
    // -----------------------------------------------------------------
    "annotation strip: 先頭 @Marker 行を drop (保険発動時)" {
        val raw = "@Snippets\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripLeadingAnnotationLines = true),
        ) shouldBe "val x = 1"
    }

    "annotation strip: 複数 @Marker 行も drop" {
        val raw = "@Foo\n@Bar\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripLeadingAnnotationLines = true),
        ) shouldBe "val x = 1"
    }

    "annotation strip: OFF (default) なら先頭 @Marker 行は保持される" {
        val raw = "@Snippets\nval x = 1"
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
    }

    // -----------------------------------------------------------------
    // stripKdoc (Logic D safety net) — KDoc が含まれた input を除外
    // -----------------------------------------------------------------
    "kdoc strip: 単行 KDoc (`/** ... */`) を drop" {
        val raw = "/** simple */\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripKdoc = true),
        ) shouldBe "val x = 1"
    }

    "kdoc strip: 複数行 KDoc を drop" {
        val raw = "/**\n * doc line\n */\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripKdoc = true),
        ) shouldBe "val x = 1"
    }

    "kdoc strip: KDoc と宣言の間の空行も drop" {
        val raw = "/**\n * doc\n */\n\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripKdoc = true),
        ) shouldBe "val x = 1"
    }

    "kdoc strip: OFF (default) なら KDoc は保持される" {
        val raw = "/**\n * doc\n */\nval x = 1"
        NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
    }

    "kdoc strip: KDoc 以外の `/* block */` コメントは drop しない (= /** で始まらないので無視)" {
        val raw = "/* not kdoc */\nval x = 1"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripKdoc = true),
        ) shouldBe raw
    }

    "kdoc strip: 宣言内部に登場する KDoc 様コメントは drop しない (leading のみ対象)" {
        val raw = "class Outer {\n    /** inner */\n    val y = 1\n}"
        NormalizeSource()(
            raw,
            NormalizeOptions.DECLARATION_DEFAULT.copy(stripKdoc = true),
        ) shouldBe raw
    }

    // -----------------------------------------------------------------
    // boundary: 既存 1 行 declaration ケースを退行させない
    // -----------------------------------------------------------------
    "boundary: 既存 1 行 property は normalize しても変わらない (idempotent + boundary)" {
        val cases = listOf(
            "val greeting = \"hello\"",
            "val x: Int = 42",
            "internal val internalProp = \"internal\"",
            "@CaptureCode annotation class Alpha",
            "data class User(val id: Long, val name: String, val email: String)",
        )
        cases.forEach { raw ->
            NormalizeSource()(raw, NormalizeOptions.DECLARATION_DEFAULT) shouldBe raw
        }
    }
})

/**
 * 行リストレベルの helper 関数を直接テストする (内部 API テスト)。
 */
class SourceNormalizerHelpersTest : StringSpec({
    "dedentLines: 最小幅 0 のときは何もしない" {
        dedentLines(listOf("a", "b")) shouldBe listOf("a", "b")
    }

    "dedentLines: 全行空ならそのまま返す" {
        dedentLines(listOf("")) shouldBe listOf("")
    }

    "dedentLines: 空白行は本来の文字数に関わらず空文字列になる" {
        dedentLines(listOf("    a", "  ", "    b")) shouldBe listOf("a", "", "b")
    }

    "trimBlankEdgeLines: 前後 blank 行 drop / 中間維持" {
        trimBlankEdgeLines(listOf("", "a", "", "b", "")) shouldBe listOf("a", "", "b")
    }

    "trimBlankEdgeLines: 全行 blank なら empty" {
        trimBlankEdgeLines(listOf("", "", "")) shouldBe emptyList()
    }

    "stripPackageAndImportLines: package + import + blank が drop される" {
        stripPackageAndImportLines(
            listOf("package a.b", "", "import c.d", "", "val x = 1"),
        ) shouldBe listOf("val x = 1")
    }

    "stripPackageAndImportLines: 最初の宣言行以降は drop しない" {
        stripPackageAndImportLines(
            listOf("package a.b", "val x = 1", "import c.d"),
        ) shouldBe listOf("val x = 1", "import c.d")
    }

    "stripLeadingAnnotationLines: 先頭 @ 行を drop" {
        stripLeadingAnnotationLines(listOf("@Foo", "@Bar", "val x = 1")) shouldBe listOf("val x = 1")
    }

    "stripLeadingAnnotationLines: 最初の非 @ 行以降は drop しない" {
        stripLeadingAnnotationLines(listOf("@Foo", "val x = 1", "@Bar")) shouldBe
            listOf("val x = 1", "@Bar")
    }

    // stripLeadingKdocLines
    "stripLeadingKdocLines: 単行 KDoc を drop" {
        stripLeadingKdocLines(listOf("/** doc */", "val x = 1")) shouldBe listOf("val x = 1")
    }

    "stripLeadingKdocLines: 複数行 KDoc を drop" {
        stripLeadingKdocLines(listOf("/**", " * doc", " */", "val x = 1")) shouldBe listOf("val x = 1")
    }

    "stripLeadingKdocLines: KDoc + blank + 宣言 → blank も drop" {
        stripLeadingKdocLines(listOf("/** doc */", "", "val x = 1")) shouldBe listOf("val x = 1")
    }

    "stripLeadingKdocLines: 最初の宣言行以降は drop しない" {
        stripLeadingKdocLines(listOf("/** doc */", "val x = 1", "/** inner */")) shouldBe
            listOf("val x = 1", "/** inner */")
    }

    "stripLeadingKdocLines: KDoc が無ければそのまま返す" {
        stripLeadingKdocLines(listOf("val x = 1")) shouldBe listOf("val x = 1")
    }

    "stripLeadingKdocLines: 空入力はそのまま" {
        stripLeadingKdocLines(emptyList()) shouldBe emptyList()
    }
})

/**
 * KDoc 用 pure helper のテスト。
 */
class KDocLookupTest : StringSpec({
    "findKDocExtendedStartOffset: KDoc が無ければ元の offset を返す" {
        val text = "val x = 1"
        findKDocExtendedStartOffset(text, 0) shouldBe 0
    }

    "findKDocExtendedStartOffset: 直前に単行 KDoc がある場合に拡張される" {
        // "/** doc */\nval x = 1"
        val text = "/** doc */\nval x = 1"
        val declStart = text.indexOf("val")
        findKDocExtendedStartOffset(text, declStart) shouldBe 0
    }

    "findKDocExtendedStartOffset: 直前に複数行 KDoc がある場合に拡張される" {
        val text = "/**\n * line\n */\nfun foo() = 1"
        val declStart = text.indexOf("fun")
        findKDocExtendedStartOffset(text, declStart) shouldBe 0
    }

    "findKDocExtendedStartOffset: KDoc 様でも `/* ... */` (block comment) は拡張対象外" {
        val text = "/* not kdoc */\nval x = 1"
        val declStart = text.indexOf("val")
        findKDocExtendedStartOffset(text, declStart) shouldBe declStart
    }

    "findKDocExtendedStartOffset: KDoc と宣言の間に空白行があっても拡張される" {
        val text = "/** doc */\n\nval x = 1"
        val declStart = text.indexOf("val")
        findKDocExtendedStartOffset(text, declStart) shouldBe 0
    }

    "findKDocExtendedStartOffset: file 内部での 2 つ目の宣言の KDoc も拡張する" {
        val text = "val a = 1\n/** doc */\nval b = 2"
        val declStart = text.indexOf("val b")
        findKDocExtendedStartOffset(text, declStart) shouldBe text.indexOf("/** doc")
    }

    "findKDocExtendedStartOffset: startOffset = 0 はそのまま" {
        findKDocExtendedStartOffset("val x = 1", 0) shouldBe 0
    }

    "findKDocExtendedStartOffset: startOffset が text 範囲外なら元の offset 返却" {
        findKDocExtendedStartOffset("val x = 1", 999) shouldBe 999
    }
})
