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
 * 2. その位置の手前 2 文字が star-slash ならばコメント終端の **候補** と見なす。
 *    ただし star-slash が行コメント内 (行頭〜 star-slash の間に最初の slash-slash が
 *    slash-star より先に現れる行) にある場合は候補にしない (slash-slash ... star-slash
 *    で終わる行コメント対策)
 * 3. 終端候補から手前に向かって **最初に見つかる** slash-star を対応する開始と見なす。
 *    slash-star-star (= KDoc) が見つかるまで無制限に遡ると、 プレーン block comment を
 *    素通りして手前の無関係な KDoc まで到達してしまうため、 最初の slash-star で確定する
 * 4. 対応開始が slash-star-star (3 文字目も star、 かつ終端の star と重ならない) で、
 *    行コメント内 (`// foo /** bar */` 対策) でもない場合に限り、 その開始位置を返す
 * 5. 上記いずれかを満たさない (KDoc が見つからない / 直前がプレーン block comment /
 *    行コメント) 場合は元の `startOffset` をそのまま返す
 *
 * KDoc と declaration の間の挙動:
 *
 * - 空白行のみ: KDoc を吸い上げて拡張する (一般的な書式)
 * - line comment / プレーン block comment が混在: KDoc 拡張は行わない (保守的に skip)
 *
 * 行コメント判定は raw text の heuristic (行内の最初の slash-slash が最初の slash-star より
 * 先かどうか) であり、 文字列リテラル内の slash-slash 等は誤検出しうるが、 その場合も
 * 「拡張しない」 側に倒れるだけで無関係なコードの混入は起きない。
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

    // 2. 直前 2 文字が star-slash であるかをチェック (コメント終端の候補)
    if (fullText[cursor] != '/' || fullText[cursor - 1] != '*') return startOffset
    val closerStar = cursor - 1 // points at '*' of the closing star-slash

    // 2'. star-slash が行コメント内 (`// see */` 等) なら KDoc 終端ではない
    if (isPrecededByLineCommentStart(fullText, closerStar)) return startOffset

    // 3. 終端候補から手前に向かって最初に見つかる slash-star を対応する開始と見なす
    var opener = -1
    var search = closerStar - 2
    while (search >= 0) {
        if (fullText[search] == '/' && fullText[search + 1] == '*') {
            opener = search
            break
        }
        search--
    }
    if (opener < 0) return startOffset

    // 4a. 対応開始が slash-star-star (= KDoc) でなければ拡張しない。
    //     3 文字目の star は終端の star と重なってはいけない (`/**/` は空 block comment)。
    if (opener + 2 >= closerStar || fullText[opener + 2] != '*') return startOffset

    // 4b. 対応開始が行コメント内 (`// foo /** bar */` 等) なら拡張しない
    if (isPrecededByLineCommentStart(fullText, opener)) return startOffset

    return opener
}

/**
 * `position` の位置がその行の行コメント (`//` 以降) に含まれると見なせるかを raw text の
 * heuristic で判定する: 行頭〜 `position` の間で、 最初の slash-slash が最初の slash-star
 * より先に現れる (または slash-star が無い) 場合に `true`。
 *
 * slash-star が slash-slash より先にある行 (例: `/** see https://example.com */` の URL 内
 * slash-slash) は行コメントとは見なさない。
 */
private fun isPrecededByLineCommentStart(fullText: String, position: Int): Boolean {
    var lineStart = position
    while (lineStart > 0 && fullText[lineStart - 1] != '\n') {
        lineStart--
    }
    val segment = fullText.substring(lineStart, position)
    val lineCommentIndex = segment.indexOf("//")
    if (lineCommentIndex < 0) return false
    val blockOpenIndex = segment.indexOf("/*")
    return blockOpenIndex < 0 || lineCommentIndex < blockOpenIndex
}

private fun Char.isWhitespaceOrNewline(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
