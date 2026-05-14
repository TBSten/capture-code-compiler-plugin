package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * Logic D の safety net: normalize 入力に KDoc コメントブロックが含まれている場合に
 * 該当行群を drop する pure function。 KDoc は slash-star-star ... star-slash の形式。
 *
 * design §5 Logic D の正規化ステップの一つとして、 `NormalizeOptions.stripKdoc = true`
 * のときに発動する。 主用途は Logic C で KDoc を含めなかった場合の二重保険:
 *
 * - Logic C (compat-k2XX [findKDocExtendedStartOffset]) が `includeKdoc = false` の
 *   ときには startOffset を前方拡張しないため、 通常はそもそも KDoc が入力に含まれず
 *   本関数は no-op
 * - しかし declaration の `startOffset` の Kotlin バージョン依存挙動 (design §7.2) で
 *   将来 KDoc が含まれてしまった場合に、 ここで保険として除去する
 *
 * アルゴリズム:
 *
 * 1. 入力 lines を先頭から走査
 * 2. trim 後 slash-star-star で始まる行を検出したら、 star-slash で終わる行 (同じ行
 *    or 後続行) まで drop
 * 3. KDoc block の後に続く空白行のみも連動 drop (KDoc と宣言の間の空行)
 * 4. 最初の non-KDoc / non-blank 行に到達したら以降はそのまま保持
 *
 * 注意: 本関数はあくまで行頭から始まる leading KDoc のみを drop する。 declaration の
 * 中身に登場する KDoc 様コメントは drop しない。
 */
public fun stripLeadingKdocLines(lines: List<String>): List<String> {
    if (lines.isEmpty()) return lines
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trimStart()
        // 空白行は KDoc block の前後で吸われる
        if (lines[i].isBlank()) {
            i++
            continue
        }
        if (!trimmed.startsWith("/**")) break
        // KDoc 開始行を発見。 同じ行に star-slash が含まれていれば 1 行 KDoc。
        if (trimmed.contains("*/")) {
            i++
            continue
        }
        // multi-line KDoc: star-slash を含む行まで drop
        i++
        while (i < lines.size) {
            val current = lines[i]
            i++
            if (current.contains("*/")) break
        }
    }
    return if (i == 0) lines else lines.subList(i, lines.size)
}
