package me.tbsten.capture.code.feature.capturedSources.ir.normalize

/**
 * 生の source text を正規化する logic class。
 *
 * design §5 Logic D に定義されたソース正規化の本体。`Source(value = "...")` filler 値に
 * 詰める前の生テキスト (`IrFileEntry.getSourceRangeInfo` から取得した素) を、
 * 設定された [NormalizeOptions] に従って整える。
 *
 * task-120 で旧 top-level `normalize()` 関数から `class NormalizeSource { operator fun invoke }`
 * へ rename。 これにより main module の他 logic class と同様の `public class XxxLogic
 * { public operator fun invoke() }` パターンに統一される。
 *
 * 処理順序:
 * 1. `"\n"` で行に split
 * 2. (declaration / file 起源) `stripLeadingKdocLines` — 先頭 KDoc block を drop (safety net)
 * 3. (declaration 起源) `stripLeadingAnnotationLines` — 先頭 `@Marker` 行を drop (保険)
 * 4. (file 起源) `stripPackageAndImportLines` — `package` / `import` 行を drop
 * 5. `dedentLines` — 共通先頭インデントを削除
 * 6. `trimBlankEdgeLines` — 先頭/末尾の空白行を drop
 * 7. `"\n"` で join
 *
 * **idempotent**: 既に正規化済みのテキストを通しても出力は変わらない (= 二度正規化しても OK)。
 * これは「1 行宣言は dedent しても変わらない」ことを保証するための重要な性質。
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.extractDeclarationSource]
 * / [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.extractFileSource]
 * / [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.extractExpressionSource])
 * は以下を保証する。 違反時は **invoke が空文字列 / no-op で安全に return する pure function**
 * のため、 `require(...)` での fail-fast は導入していない (= 入力 text の各 edge case で
 * 静的に正常 fallback)。
 *
 * - `rawText: String` は file 由来の raw source snippet (= ExtractSourceText 経由)。 空文字列は
 *   早期 return で `""` を返す。 CRLF / CR は LF に正規化される。
 * - `options: NormalizeOptions` は caller が `toDeclarationNormalizeOptions` /
 *   `toFileNormalizeOptions` / `toExpressionNormalizeOptions` で構築した値。 [NormalizeOptions]
 *   の各 flag は独立に on/off 可能で、 互いに副作用なし。
 * - 各 sub helper ([dedentLines] / [trimBlankEdgeLines] / [stripPackageAndImportLines] /
 *   [stripLeadingAnnotationLines] / [stripLeadingKdocLines] / [findKDocExtendedStartOffset])
 *   はすべて pure function で、 入力に対して decidable な変換のみ行う。 各 helper の前提条件は
 *   個別 KDoc を参照。 主要な不変条件は以下:
 *   - `dedentLines`: 全 blank 入力でも safe (= 全 `""` を返す)。
 *   - `trimBlankEdgeLines`: 空入力なら空を返す。 全 blank なら `emptyList()`。
 *   - `stripPackageAndImportLines`: `package ` / `import ` 始まり以外の行で停止 (= 中間 line で
 *     stripping を継続しない)。
 *   - `stripLeadingAnnotationLines`: 行頭 `@` の連続を drop、 KDoc 行 / line comment は除外しない。
 *   - `stripLeadingKdocLines`: 先頭の `/** ... */` block のみ drop (= 中間 KDoc は drop しない)。
 */
public class NormalizeSource {

    /**
     * @param rawText 生のソーステキスト。`IrFileEntry.getSourceRangeInfo(...).text` のような形式。
     * @param options 正規化設定 ([NormalizeOptions.DECLARATION_DEFAULT] / [NormalizeOptions.FILE_DEFAULT] / [NormalizeOptions.EXPRESSION_DEFAULT] 等)。
     * @return 正規化されたソーステキスト。改行は LF (`'\n'`) で正規化される。
     */
    public operator fun invoke(rawText: String, options: NormalizeOptions): String {
        if (rawText.isEmpty()) return ""

        // CRLF / CR を LF に正規化してから処理することで、後段の split / join を単純化する。
        val lf = rawText.normalizeLineEndings()

        var lines: List<String> = lf.split('\n')

        if (options.stripKdoc) {
            lines = stripLeadingKdocLines(lines)
        }

        if (options.stripLeadingAnnotationLines) {
            lines = stripLeadingAnnotationLines(lines)
        }

        if (options.stripPackageAndImport) {
            lines = stripPackageAndImportLines(lines)
        }

        if (options.dedent) {
            lines = dedentLines(lines)
        }

        if (options.trimBlankEdges) {
            lines = trimBlankEdgeLines(lines)
        }

        return lines.joinToString("\n")
    }

    /**
     * CRLF / CR を LF に正規化する。出力のソース文字列は LF 統一であることを保証する。
     */
    private fun String.normalizeLineEndings(): String {
        if (!contains('\r')) return this
        return replace("\r\n", "\n").replace('\r', '\n')
    }
}
