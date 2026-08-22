package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * marker annotation 行のテキスト照合に使う名前解決 helper 群 (bug-005 で導入)。
 *
 * `skipLeadingMarkerAnnotations` / `skipLeadingAnnotationLines` は `@` の直後を simple name として
 * 読み、 marker simple name 集合と文字列一致で marker 判定するが、 修正前は
 *
 * - `@example.Snippet` (FQN 記法) → 最初の identifier `example` で読み終わる → 不一致 → 非 marker 扱い
 * - `import example.Snippet as Snip` + `@Snip` (import alias) → `Snip` は simple name 集合に無い → 不一致
 *
 * となり、 marker 行が「ユーザが書いた他の annotation」 と同じ扱いで capture に leak していた。
 *
 * 本ファイルは
 *
 * 1. [readQualifiedAnnotationName] — `@` の後を `.` 区切りの qualified name として読み進め、
 *    **末尾 segment** を simple name として返す (FQN 記法対応)
 * 2. [markerImportAliases] — file 先頭の import list から `import <fqn> as <alias>` を解析し、
 *    `<fqn>` が登録済み marker のとき `<alias>` を返す (import alias 対応)
 *
 * を提供する。 `CollectDeclarationSite.kt` が 500 行制限を超過しているため、 同 package の
 * 別ファイルとして切り出している。
 */

/**
 * [readQualifiedAnnotationName] の戻り値。
 *
 * - [nameEnd] : qualified name 全体 (`example.Snippet` 等) の終端 offset (exclusive)。
 *   annotation argument `(...)` の走査はこの offset から続行する。
 * - [simpleName] : qualified name の **末尾 segment** (= `@example.Snippet` なら `Snippet`)。
 *   name が読めなかった場合は空文字列。
 */
internal data class QualifiedAnnotationName(
    val nameEnd: Int,
    val simpleName: String,
)

/**
 * [nameStart] (= `@` の直後) から `.` 区切りの qualified annotation name を読み進める。
 *
 * - `@Snippet` → nameEnd は `t` の直後、 simpleName = `"Snippet"`
 * - `@example.Snippet` → nameEnd は `t` の直後、 simpleName = `"Snippet"` (末尾 segment)
 * - `@example.` (`.` の後に identifier が無い) → `example` までで打ち切り (`.` は消費しない)
 * - `@` 単独 (identifier 無し) → nameEnd = nameStart, simpleName = `""`
 *
 * identifier は `isLetterOrDigit() || '_'` の連続として読む (backtick identifier は非対応 =
 * 従来と同じ制限)。 use-site target (`@get:Snippet`) の `:` は identifier に含まれないため
 * `get` で読み終わり、 従来どおり非 marker 扱いになる (issue 05 (3) は別対応)。
 */
internal fun readQualifiedAnnotationName(
    text: String,
    nameStart: Int,
    endOffset: Int,
): QualifiedAnnotationName {
    fun readIdentifierEnd(from: Int): Int {
        var i = from
        while (i < endOffset) {
            val ch = text[i]
            if (ch.isLetterOrDigit() || ch == '_') i++ else break
        }
        return i
    }

    val firstSegmentEnd = readIdentifierEnd(nameStart)
    if (firstSegmentEnd == nameStart) {
        return QualifiedAnnotationName(nameEnd = nameStart, simpleName = "")
    }
    var lastSegmentStart = nameStart
    var nameEnd = firstSegmentEnd
    while (nameEnd < endOffset && text[nameEnd] == '.') {
        val segmentStart = nameEnd + 1
        val segmentEnd = readIdentifierEnd(segmentStart)
        if (segmentEnd == segmentStart) break // `.` の後に identifier が無い → 打ち切り (`.` は残す)
        lastSegmentStart = segmentStart
        nameEnd = segmentEnd
    }
    return QualifiedAnnotationName(
        nameEnd = nameEnd,
        simpleName = text.substring(lastSegmentStart, nameEnd),
    )
}

/**
 * `import <fqn> as <alias>` 形式の import 行のみ抽出する regex。
 * trim 済みの 1 行に対して [Regex.matchEntire] で適用する (行末の `;` / line comment は許容)。
 */
private val IMPORT_ALIAS_REGEX = Regex("""import\s+([\w.]+)\s+as\s+(\w+)\s*;?\s*(?://.*)?""")

/**
 * [fileText] の import list から、 **登録済み marker FqN への import alias** の集合を返す。
 *
 * `import example.Snippet as Snip` のような alias import は、 当該 file 内では `@Snip` として
 * marker を書けるため、 marker simple name 集合に `<alias>` を追加する必要がある
 * (= [markerSimpleNames] が本関数の結果を union する)。 alias 先の FqN が
 * [CaptureCodeMarkerRegistry.markerFqns] に含まれない場合 (= 非 marker annotation への alias) は
 * 追加しない (= `@Alias` 行は従来どおり source に残る)。
 *
 * 走査は file 先頭の header 領域 (blank / comment / `@file:` / `package` / `import`) に限定し、
 * 最初の宣言行に到達したら打ち切る。 これにより multi-line string literal 内の
 * `"import x as y"` のような偽 import 行を alias と誤認しない。
 */
internal fun markerImportAliases(fileText: String): Set<String> {
    val markerFqns = CaptureCodeMarkerRegistry.markerFqns
    if (markerFqns.isEmpty()) return emptySet()
    val aliases = mutableSetOf<String>()
    var inBlockComment = false
    for (rawLine in fileText.lineSequence()) {
        var line = rawLine.trim()
        if (inBlockComment) {
            val end = line.indexOf("*/")
            if (end < 0) continue
            line = line.substring(end + 2).trim()
            inBlockComment = false
        }
        // 行頭の block comment (`/* ... */` / `/** ... */`) を処理。 行内で閉じない場合は
        // 以降の行を comment として読み飛ばす。
        while (line.startsWith("/*")) {
            val end = line.indexOf("*/", startIndex = 2)
            if (end < 0) {
                inBlockComment = true
                break
            }
            line = line.substring(end + 2).trim()
        }
        if (inBlockComment) continue
        when {
            line.isEmpty() -> continue
            line.startsWith("//") -> continue
            line.startsWith("*") -> continue // block comment 中間行の装飾 (保険)
            line.startsWith("@file:") -> continue
            line.startsWith("package ") || line == "package" -> continue
            line.startsWith("import ") -> {
                val match = IMPORT_ALIAS_REGEX.matchEntire(line) ?: continue
                val (fqn, alias) = match.destructured
                if (fqn in markerFqns) aliases += alias
            }
            // import list の終端 (= 最初の宣言行) に到達 → 以降に import は現れない
            else -> return aliases
        }
    }
    return aliases
}
