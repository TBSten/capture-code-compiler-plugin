package me.tbsten.capture.code.feature.capturedSources.ir.normalize

/**
 * Logic C 補助: 与えられた `fullText` と declaration の `startOffset` から、
 * declaration の直前に位置する KDoc コメントブロックを含めるように `startOffset` を
 * 前方に拡張する pure helper。 KDoc は slash-star-star ... star-slash の形式。
 *
 * Kotlin 2.x の IR では `IrDeclaration.startOffset` は KDoc を含まず、最初の
 * `@Marker` annotation 行 (もしあれば) もしくは declaration 本体の先頭を指す
 * (design §7.2 / [me.tbsten.capture.code.CaptureCodePluginConfig.includeKdoc] のドキュメント参照)。
 *
 * 本関数は PSI に依存せず raw text のみで KDoc 範囲を検出する:
 *
 * 1. `startOffset` から手前に向かって whitespace / newline を skip
 * 2. その位置の手前 2 文字が star-slash ならば KDoc 終端と見なし、 さらに手前に向かって
 *    対応する slash-star-star を探す
 * 3. 見つかった slash-star-star の開始位置を返す
 * 4. KDoc が見つからない / 形式が不正な場合は元の `startOffset` をそのまま返す
 *
 * KDoc と declaration の間の挙動:
 *
 * - 空白行のみ: KDoc を吸い上げて拡張する (一般的な書式)
 * - line comment が混在: KDoc 拡張は行わない (保守的に skip)
 *
 * idempotent: 既に KDoc を含む offset を渡しても再拡張は行わない。
 *
 * @param fullText file 全体のソース文字列。
 * @param startOffset 元の declaration `startOffset`。
 * @return KDoc を含むように前方拡張された offset (見つからなければそのまま)。
 */
public fun findKDocExtendedStartOffset(fullText: String, startOffset: Int): Int {
    if (startOffset <= 0 || startOffset > fullText.length) return startOffset

    // 1. startOffset から手前に whitespace / newline を skip
    var cursor = startOffset - 1
    while (cursor >= 0 && fullText[cursor].isWhitespaceOrNewline()) {
        cursor--
    }
    if (cursor < 1) return startOffset

    // 2. 直前 2 文字が star-slash であるかをチェック (KDoc 終端)
    if (fullText[cursor] != '/' || fullText[cursor - 1] != '*') return startOffset

    // 3. star-slash の直前から手前に向かって slash-star-star を探す
    val endOfKdoc = cursor - 1 // points at '*'
    var search = endOfKdoc - 1
    while (search >= 1) {
        if (fullText[search] == '*' && fullText[search - 1] == '/') {
            // 候補: slash-star を発見。 KDoc は slash-star-star なので、 search+1 が '*' であることを確認。
            if (search + 1 <= endOfKdoc && fullText[search + 1] == '*') {
                return search - 1
            }
        }
        search--
    }
    // 対応する slash-star-star が見つからない場合は拡張しない
    return startOffset
}

private fun Char.isWhitespaceOrNewline(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
