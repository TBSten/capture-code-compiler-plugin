package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.findKDocExtendedStartOffset

/**
 * Logic B-ir: declaration / file annotation / expression site の収集本体。
 *
 * task-120 (IR logic 移行) で、 各 `compat-kXXX/K{XXX}CapturedSourcesCollector.kt` の **pure 部分**
 * (string offset 操作、 marker simpleName 抽出、 file path matching 等) を main module に集約した版。
 *
 * ## 責務分担
 *
 * 本 class は **drift しない pure helpers** を提供する:
 * - [expandStartToCoverModifierAndAnnotationLines] — Kotlin 2.2+ で declaration の startOffset が
 *   modifier / annotation 行を含まなくなった drift を吸収するための offset 補正
 * - [skipLeadingAnnotationLines] — 行頭の marker annotation token を skip
 * - [extractKdocPrefix] — declaration 直前 KDoc を抽出
 * - [stripSurroundingParens] — `(expr)` 形式の両端 paren を strip
 * - [matchesFile] — file path 末尾一致判定
 *
 * **IR 走査本体 (= visitor base class drift)** は引き続き compat-kXXX 側に残る:
 * - K200-K210: `IrElementVisitorVoid` (interface)
 * - K220+: `IrVisitorVoid` (class)
 * - 上記の drift は K2.0 baseline では吸収できないため、 compat 各 module が override を持つ
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 ただし本 class は invoke を「helpers のエントリ集約」として宣言する
 * のではなく、 純粋に **utility 関数の集合** として使う。 invoke を呼ぶよりも各 helper を直接
 * 呼ぶことが多いため、 invoke は no-op の placeholder で defined。
 *
 * (将来 task で IR walker drift 自体を CompatContext で吸収して main 側に集約する場合、
 * 本 class の invoke が moduleFragment 全体を受け取って `List<CollectedSiteData>` を返す
 * orchestrator になる予定。)
 */
public class CollectDeclarationSite {

    /**
     * Reserved entrypoint。 現状は **fail-fast placeholder**。 task-120-B で `moduleFragment`
     * を受け取って `List<CollectedSiteData>` を返す orchestrator になる予定。
     *
     * Helper methods ([expandStartToCoverModifierAndAnnotationLines], [skipLeadingAnnotationLines],
     * [extractKdocPrefix], [stripSurroundingParens], [matchesFile]) は本 invoke を経由せず
     * 個別に呼び出して利用する。
     */
    public operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )

    /**
     * `startOffset` を行頭まで遡らせ、 さらに直前の行が **declaration modifier のみで構成された行**
     * または **annotation 行 (`@<Name>` で始まる行)** であれば、 さらに前の行までスキャンして
     * 拡張する。 KDoc コメントブロックや declaration 本体行に到達した時点で停止する。
     *
     * Kotlin 2.2.x 以降の IR では `IrDeclaration.startOffset` が宣言キーワード (`val` / `fun` /
     * `class` / `object` / `typealias`) の位置を指し、 modifier / annotation を含まない。
     * K200/K210 baseline では `@Marker` 行を含む位置を指していたため、 本メソッドは 2.2+ で
     * baseline と同等の startOffset を再構成する補正レイヤ。
     */
    public fun expandStartToCoverModifierAndAnnotationLines(fullText: String, startOffset: Int): Int {
        if (startOffset <= 0 || startOffset > fullText.length) return startOffset

        val lineStart = lineStartOffsetOf(fullText, startOffset)
        val prefix = fullText.substring(lineStart, startOffset)
        var current = if (prefix.isBlank() || prefixIsAllModifierTokens(prefix)) lineStart else startOffset

        if (current != lineStart) return current
        while (current > 0) {
            val prevLineEnd = current - 1
            if (prevLineEnd < 0) break
            if (fullText[prevLineEnd] != '\n') break
            val prevLineStart = lineStartOffsetOf(fullText, prevLineEnd)
            val prevLine = fullText.substring(prevLineStart, prevLineEnd)
            if (!isModifierOrAnnotationLine(prevLine)) break
            current = prevLineStart
        }
        return current
    }

    /**
     * 行頭 `lineStart` から `startOffset` までの prefix が、 空白とすべて modifier token
     * (例: `const`, `suspend`, `inline` …) のみで構成されているかを判定する。
     */
    private fun prefixIsAllModifierTokens(prefix: String): Boolean {
        val trimmed = prefix.trim()
        if (trimmed.isEmpty()) return true
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.all { it in DECLARATION_MODIFIERS }
    }

    /** `offset` を含む行の行頭 offset (= 直前の `\n` の次、 または 0) を返す。 */
    private fun lineStartOffsetOf(text: String, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    /**
     * 行が「modifier のみで構成された行」または「annotation で始まる行 (`@...`)」であるかを判定する。
     */
    private fun isModifierOrAnnotationLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return false
        if (trimmed.startsWith("//")) return false
        if (trimmed.startsWith("@")) return true
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.all { it in DECLARATION_MODIFIERS }
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、 先頭の **marker annotation 行** (改行まで) を
     * スキップした offset を返す。
     *
     * marker annotation **そのもの** (`@<simpleName>` + optional `(<args>)`) を token として識別し、
     * 末尾の空白 (改行を含む) を吸収する。 marker でない annotation (`@JvmInline` 等) はソースとして残す。
     *
     * @param markerSimpleNames marker FqN の simple name 集合 (= class 名のみ抜き出したもの)
     */
    public fun skipLeadingAnnotationLines(
        text: String,
        startOffset: Int,
        endOffset: Int,
        markerSimpleNames: Set<String>,
    ): Int {
        var cursor = startOffset
        while (cursor < endOffset) {
            val lineStart = cursor
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor >= endOffset || text[cursor] != '@') {
                return lineStart
            }
            val nameStart = cursor + 1
            var nameEnd = nameStart
            while (nameEnd < endOffset) {
                val ch = text[nameEnd]
                if (ch.isLetterOrDigit() || ch == '_') nameEnd++ else break
            }
            val simpleName = if (nameEnd > nameStart) text.substring(nameStart, nameEnd) else ""
            if (simpleName !in markerSimpleNames) {
                return lineStart
            }
            cursor = nameEnd
            if (cursor < endOffset && text[cursor] == '(') {
                var depth = 0
                while (cursor < endOffset) {
                    when (text[cursor]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) {
                                cursor++
                                break
                            }
                        }
                    }
                    cursor++
                }
                if (depth != 0) return lineStart
            }
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor < endOffset && text[cursor] == '\n') {
                cursor++
            }
        }
        return cursor
    }

    /**
     * declaration の `startOffset` 直前に位置する KDoc コメントブロック (`/** ... */`) を
     * raw text として抽出する。 KDoc が見つからない場合は空文字列を返す。
     */
    public fun extractKdocPrefix(fullText: String, startOffset: Int): String {
        val kdocStart = findKDocExtendedStartOffset(fullText, startOffset)
        if (kdocStart >= startOffset) return ""
        var lineStart = kdocStart
        while (lineStart > 0 && fullText[lineStart - 1] != '\n') {
            val ch = fullText[lineStart - 1]
            if (ch != ' ' && ch != '\t') break
            lineStart--
        }
        return fullText.substring(lineStart, startOffset).trimEnd()
    }

    /**
     * 両端を **対応する 1 ペアの括弧で完全に囲まれている** かつ **内部が `{` `}` で始まる lambda
     * 形式ではない** 場合に限り、 最外殻 `(` `)` を取り除く。
     *
     * 具体例:
     * - `"(1 + 2)"` → `"1 + 2"`
     * - `"({ println(\"x\") })"` → `"({ println(\"x\") })"` (parenthesis-lambda 形式なので保持)
     * - `"run { ... }"` → `"run { ... }"` (`(` で始まらないので無変更)
     */
    public fun stripSurroundingParens(text: String): String {
        if (text.length < 2) return text
        if (text.first() != '(' || text.last() != ')') return text
        val inner = text.substring(1, text.length - 1)
        val trimmedInner = inner.trimStart()
        if (trimmedInner.startsWith('{')) return text
        var depth = 0
        for ((index, ch) in text.withIndex()) {
            when (ch) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && index != text.lastIndex) return text
                }
            }
        }
        return if (depth == 0) text.substring(1, text.length - 1) else text
    }

    /**
     * registry に登録された site の filePath が IR file path に一致するかを判定する。
     *
     * 一致条件 (いずれか):
     * 1. site.filePath == irFilePath (= 絶対パス完全一致)
     * 2. site.filePath が irFilePath の末尾要素と一致 (= ファイル名のみで一致)
     * 3. irFilePath が site.filePath の末尾要素と一致 (= 逆方向)
     */
    public fun matchesFile(site: CaptureCodeExpressionSiteRegistry.Site, irFilePath: String): Boolean {
        val sitePath = site.filePath
        if (sitePath == irFilePath) return true
        val siteLeaf = sitePath.substringAfterLast('/').substringAfterLast('\\')
        val irLeaf = irFilePath.substringAfterLast('/').substringAfterLast('\\')
        if (siteLeaf == irLeaf && siteLeaf.isNotEmpty()) return true
        if (irFilePath.endsWith(sitePath) || sitePath.endsWith(irFilePath)) return true
        return false
    }

    public companion object {
        /**
         * Kotlin の declaration modifier 集合。 K200 baseline で startOffset が含んでいたが、
         * Kotlin 2.2+ では除外されるため、 これらを行レベルで吸い戻すために使う。
         */
        public val DECLARATION_MODIFIERS: Set<String> = setOf(
            "public", "private", "protected", "internal",
            "open", "final", "abstract", "sealed", "override",
            "data", "inner", "value", "enum", "annotation", "companion",
            "suspend", "inline", "noinline", "crossinline", "tailrec",
            "operator", "infix", "external",
            "const", "lateinit",
            "reified", "vararg",
            "expect", "actual",
        )
    }
}
