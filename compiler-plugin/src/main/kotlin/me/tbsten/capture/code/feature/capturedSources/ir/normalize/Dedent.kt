package me.tbsten.capture.code.feature.capturedSources.ir.normalize

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
 * ## `ignoreFirstLine` (bug-006)
 *
 * 式起源の抽出 text は 1 行目が式そのもの (= 行の途中) から始まることがあり、 その場合の
 * 1 行目は元 file のインデント情報を持たない (見かけ上 0 インデント)。 これを最小幅計算に
 * 含めると `minIndent = 0` と判定されて 2 行目以降が元 file の絶対インデントのまま残る
 * (`val v = @Marker() run { ... }` のような行の途中から始まる式で崩れる)。
 *
 * `ignoreFirstLine = true` かつ複数行のときは:
 *
 * - `minIndent` は **2 行目以降の非空行** から計算する (2 行目以降が全 blank なら従来どおり
 *   全行から計算する)
 * - 1 行目は自身の leading whitespace の範囲内でのみ dedent する
 *   (= `min(minIndent, 1 行目の indent)` 文字を削除)。 行頭開始式で 1 行目にインデントが
 *   復元されている場合 (`reattachOwnLeadingIndent` 経路) は残り行の最小幅と一致するため、
 *   従来の出力と同一になる。 行の途中から始まる式 (indent 0) では 1 行目は不変
 *
 * @param lines `splitToSequence("\n")` などで分割した行のリスト (改行文字は含まない)。
 * @param ignoreFirstLine 最小インデント幅の計算から 1 行目を除外するか (式起源のみ true)。
 * @return 各行から共通インデントを取り除いた新しい行リスト。
 */
public fun dedentLines(lines: List<String>, ignoreFirstLine: Boolean = false): List<String> {
    val excludeFirst = ignoreFirstLine && lines.size > 1
    val minIndent = (if (excludeFirst) lines.subList(1, lines.size) else lines)
        .filter { it.isNotBlank() }
        .minOfOrNull { it.leadingWhitespaceWidth() }
        // 2 行目以降が全 blank の場合は従来どおり全行 (= 実質 1 行目のみ) から計算する
        ?: lines.filter { it.isNotBlank() }.minOfOrNull { it.leadingWhitespaceWidth() }
        ?: return lines.map { if (it.isBlank()) "" else it }

    if (minIndent == 0) {
        // 削除なしだが空白行の正規化だけは適用する (1 行入力など)。
        return lines.map { if (it.isBlank()) "" else it }
    }

    return lines.mapIndexed { index, line ->
        when {
            line.isBlank() -> ""
            excludeFirst && index == 0 ->
                line.substring(minOf(minIndent, line.leadingWhitespaceWidth()))
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
