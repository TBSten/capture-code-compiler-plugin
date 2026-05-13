package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * 行リストの dedent (= 共通の先頭インデントを削除する) を行う pure function。
 *
 * アルゴリズム:
 * 1. 各行の先頭 whitespace 幅 (space + tab、混在は文字数として一律カウント) を計算する。
 *    ただし `isBlank()` な行は計算対象から除外する。
 * 2. 全行の最小幅 `minIndent` を取得する。非空行が存在しないなら 0 を返し、全行をそのまま返す。
 * 3. 各行から `minIndent` 文字を削除する。
 *    - 非空行の先頭は必ず `minIndent` 以上の whitespace を持つので素直に substring。
 *    - 空白行は元の長さに関わらず `""` に正規化する (= 末尾空白の保持はしない)。
 *      これにより `"    "` のような不揃いな空白行で末尾に空白が残ることを防ぐ。
 *
 * Note: 1 行入力 (改行なし) でも素直に動く。先頭インデントがあれば dedent され、なければそのまま。
 *
 * @param lines `splitToSequence("\n")` などで分割した行のリスト (改行文字は含まない)。
 * @return 各行から共通インデントを取り除いた新しい行リスト。
 */
public fun dedentLines(lines: List<String>): List<String> {
    val minIndent = lines
        .filter { it.isNotBlank() }
        .minOfOrNull { it.leadingWhitespaceWidth() }
        ?: return lines.map { if (it.isBlank()) "" else it }

    if (minIndent == 0) {
        // 削除なしだが空白行の正規化だけは適用する (1 行入力など)。
        return lines.map { if (it.isBlank()) "" else it }
    }

    return lines.map { line ->
        when {
            line.isBlank() -> ""
            else -> line.substring(minIndent)
        }
    }
}

/**
 * 行の先頭 whitespace 幅 (= space / tab の連続) を返す。
 * 制御文字や non-breaking space は対象外で、Kotlin の `Char.isWhitespace()` のうち
 * space (`' '`) と tab (`'\t'`) のみを数える。これにより全角空白などが
 * 「夾雑物」として誤検出されてインデント幅に含まれてしまうことを防ぐ。
 */
private fun String.leadingWhitespaceWidth(): Int {
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c != ' ' && c != '\t') break
        i++
    }
    return i
}
