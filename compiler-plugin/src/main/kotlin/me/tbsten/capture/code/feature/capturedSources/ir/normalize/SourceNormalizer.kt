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
