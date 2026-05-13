package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * 行リストの先頭と末尾から `isBlank()` な行 (空文字列 / whitespace のみ) を drop する pure function。
 * 中間の空白行は保持する (文中の段落区切りを潰さないため)。
 *
 * すべての行が blank の場合は空のリストを返す。
 */
internal fun trimBlankEdgeLines(lines: List<String>): List<String> {
    if (lines.isEmpty()) return lines

    val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
    if (firstNonBlank == -1) return emptyList()

    val lastNonBlank = lines.indexOfLast { it.isNotBlank() }
    return lines.subList(firstNonBlank, lastNonBlank + 1)
}
