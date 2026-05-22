package me.tbsten.capture.code.feature.capturedSources.ir.normalize

/**
 * file 起源の正規化で `package` 宣言行と `import` 行を除外する pure function。
 *
 * 仕様:
 * - **先頭の `@file:` annotation 行** (= `@file:Marker`) は skip して保持する。
 *   - これは `NormalizeOptions.stripLeadingAnnotationLines = false`
 *     (= `includeAnnotationLines = true`) のときに `@file:Marker` が leading に残った
 *     状態で本関数が呼ばれるケースに備える (task-143 BUG-3-2 fix)。
 * - 続いて、`package ` または `import ` で始まる行 (trim 後) を drop する。
 * - blank line も連動して drop する (= package/import の間にある空行は捨てる)。
 * - **最初の non-package / non-import / non-blank 行に到達したら以降はそのまま保持**。
 *   - これにより中間に意図的に置かれた `import` 様文字列 (e.g. docstring 内) は保持される。
 *
 * KtFile の整形を意識しているため、Kotlin の `package` と `import` を厳格に検出する。
 * package / import の **継続行** (Kotlin 文法では存在しないが将来形式変更に備える) は考慮しない。
 *
 * **task-143 fix (BUG-3-2)**: 旧仕様は「先頭から greedy で break」 のみで、 leading に
 * `@file:Marker` が存在する場合に最初の non-package / non-import 行で即 break して
 * 後続の `package` / `import` が drop されない bypass bug が発生していた
 * (`includeAnnotationLines = true` × `includeImports = false` の組み合わせ)。
 * 先頭の `@file:` annotation 行を skip してから greedy drop を続けることで、
 * `includeAnnotationLines` の値と独立して `includeImports = false` が効くようにする。
 */
public fun stripPackageAndImportLines(lines: List<String>): List<String> {
    // 先頭の `@file:` annotation 行を skip (task-143)。 `@file:Marker` 様の line が
    // 続く限り index を進める。 通常は 0 〜 1 行で終わる (1 ファイルにつき
    // `@file:` annotation は理論上複数置けるが現実的には少ない)。
    var annotationEnd = 0
    while (annotationEnd < lines.size && lines[annotationEnd].trimStart().startsWith("@file:")) {
        annotationEnd++
    }
    var i = annotationEnd
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
    return if (i == annotationEnd) {
        lines
    } else {
        lines.subList(0, annotationEnd) + lines.subList(i, lines.size)
    }
}
