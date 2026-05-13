package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * file 起源の正規化で `package` 宣言行と `import` 行を除外する pure function。
 *
 * 仕様:
 * - 先頭から見て、`package ` または `import ` で始まる行 (trim 後) を drop する。
 * - blank line も連動して drop する (= package/import の間にある空行は捨てる)。
 * - **最初の non-package / non-import / non-blank 行に到達したら以降はそのまま保持**。
 *   - これにより中間に意図的に置かれた `import` 様文字列 (e.g. docstring 内) は保持される。
 *
 * KtFile の整形を意識しているため、Kotlin の `package` と `import` を厳格に検出する。
 * package / import の **継続行** (Kotlin 文法では存在しないが将来形式変更に備える) は考慮しない。
 */
public fun stripPackageAndImportLines(lines: List<String>): List<String> {
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trimStart()
        val isPackageOrImport = trimmed.startsWith("package ") ||
            trimmed.startsWith("import ") ||
            trimmed == "package" ||
            trimmed == "import"
        val isBlank = lines[i].isBlank()
        if (!isPackageOrImport && !isBlank) break
        i++
    }
    return if (i == 0) lines else lines.subList(i, lines.size)
}
