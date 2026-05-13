package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * 生の source text を正規化する pure function。
 *
 * design §5 Logic D に定義されたソース正規化の本体。`Source(value = "...")` filler 値に
 * 詰める前の生テキスト (task-013 で `IrFileEntry.getSourceRangeInfo` から取得した素) を、
 * 設定された [NormalizeOptions] に従って整える。
 *
 * 処理順序:
 * 1. `"\n"` で行に split
 * 2. (declaration 起源) `stripLeadingAnnotationLines` — 先頭 `@Marker` 行を drop (保険)
 * 3. (file 起源) `stripPackageAndImportLines` — `package` / `import` 行を drop
 * 4. `dedentLines` — 共通先頭インデントを削除
 * 5. `trimBlankEdgeLines` — 先頭/末尾の空白行を drop
 * 6. `"\n"` で join
 *
 * **idempotent**: 既に正規化済みのテキストを通しても出力は変わらない (= 二度正規化しても OK)。
 * これは「1 行宣言は dedent しても変わらない」ことを保証するための重要な性質。
 *
 * ## 配置 (task-013 で `:compiler-plugin` から `:compiler-plugin:compat` へ移動)
 *
 * task-015 では本 module は `:compiler-plugin/main` 配下にあり `internal` だったが、
 * task-013 で `:compat-k2000` の IR transformer から wire up する必要が出たため、
 * `:compat` (compat module) へ物理移動し `public` 化した。`:compat` は kotlin-compiler-embeddable
 * への compileOnly のみで pure Kotlin 関数を内包でき、`:compat-k2000` と `:compiler-plugin` の
 * 両方から共有できる SSOT になる。
 *
 * @param rawText 生のソーステキスト。`IrFileEntry.getSourceRangeInfo(...).text` のような形式。
 * @param options 正規化設定 ([NormalizeOptions.DECLARATION_DEFAULT] / [NormalizeOptions.FILE_DEFAULT] / [NormalizeOptions.EXPRESSION_DEFAULT] 等)。
 * @return 正規化されたソーステキスト。改行は LF (`'\n'`) で正規化される。
 */
public fun normalize(rawText: String, options: NormalizeOptions): String {
    if (rawText.isEmpty()) return ""

    // CRLF / CR を LF に正規化してから処理することで、後段の split / join を単純化する。
    val lf = rawText.normalizeLineEndings()

    var lines: List<String> = lf.split('\n')

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
