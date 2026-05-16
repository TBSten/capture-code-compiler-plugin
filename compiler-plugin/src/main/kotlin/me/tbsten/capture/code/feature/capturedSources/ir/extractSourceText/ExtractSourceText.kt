package me.tbsten.capture.code.feature.capturedSources.ir.extractSourceText

/**
 * Logic C: declaration / expression site の source text 抽出。
 *
 * task-120 (IR logic 移行) で各 `compat-kXXX/SourceTextExtractor.kt` の **pure 部分**
 * (`substringOrNull`) を main module に集約した版。 PSI access が必要な部分
 * (`loadFileText(IrFile): String?`) は compat layer の責務として残しているため、
 * 本 class はその両者を組み合わせる呼び出し側 (compat-kXXX の collector) から `substringOrNull`
 * を直接使ってもらう想定。
 *
 * ## 実装
 *
 * - [substringOrNull] (pure) - file 全体テキストと `[startOffset, endOffset)` から該当範囲を切り出す
 * - PSI 経由の file text loading (drift 大) - compat-kXXX 側に残す (`SourceTextExtractor.loadFileText`)
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 旧 `SourceTextExtractor.substringOrNull(...)` の top-level object 関数
 * 形式から、 class + invoke パターンに整理した。
 *
 * 状態を持たないので invoke は instance method として呼べる (`ExtractSourceText()(text, start, end)`)。
 */
public class ExtractSourceText {

    /**
     * [fullText] の `[startOffset, endOffset)` 区間をそのまま substring する pure helper。
     *
     * 範囲が不正 (負値 / 反転 / fullText.length 超過) なら `null`。 本関数は raw substring のみで、
     * dedent や annotation 行除外などの正規化は行わない (それらは
     * [me.tbsten.capture.code.feature.capturedSources.ir.normalize.NormalizeSource] の責務)。
     *
     * @param fullText 抽出元となる file 全体テキスト
     * @param startOffset 抽出開始 offset (inclusive、 0-based)
     * @param endOffset 抽出終了 offset (exclusive、 0-based)
     * @return 抽出された文字列。 範囲不正なら `null`。
     */
    public operator fun invoke(fullText: String, startOffset: Int, endOffset: Int): String? {
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
        if (endOffset > fullText.length) return null
        return fullText.substring(startOffset, endOffset)
    }
}
