package me.tbsten.capture.code.compat.k2000

import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import java.io.File

/**
 * Logic C (design §5.C) のソーステキスト取得本体。Kotlin 2.0.0 (`compat-k2000`) 向け実装。
 *
 * IR phase で declaration や file annotation の `startOffset..endOffset` から **生テキスト** を
 * 取り出す。`IrFileEntry.getSourceRangeInfo` は行列情報しか保持しないため、本 extractor は
 * 2 系統のフォールバック経路を持つ:
 *
 * 1. **PSI 経由** ([PsiIrFileEntry.psiFile.text]): PSI が利用可能ならその text を使う
 *    (offset 整合が確実)。
 * 2. **filesystem 経由**: PSI が無い (LightTree / `NaiveSourceBasedFileEntryImpl` など) 場合は
 *    [IrFile.fileEntry] の `name` (絶対パス) から `File.readText()` する。design §7.3 / R3 対応。
 *
 * ## 配置 (task-013 の設計判断)
 *
 * task-013 のチケット §「影響範囲」では `:compiler-plugin` 配下 (`feature/captured_sources/
 * SourceTextExtractor.kt`) に置く案だったが、本 extractor は `IrFile` (= IR API) を直接受け取る
 * ため `:compat-k2000` 配下に置く方が依存関係としても自然 (`:compiler-plugin` は :compat-k2000
 * に依存しているため、逆方向に IR API を持ち込むと循環依存になりかねない)。
 *
 * Kotlin バージョン違いで PSI / fileEntry API が変わったら、本 file を `:compat-kXXXX` 各 module
 * で並列に書き換える形になる (= compat layer の意義そのまま)。同等の logic を `:compat` 共通に
 * 置くべきかは将来 `core/` module 切り出し検討時に再評価する。
 */
internal object SourceTextExtractor {

    /**
     * 与えられた [IrFile] のソーステキスト全体を返す。取得できなければ `null`。
     *
     * 取得経路:
     * 1. PSI text (`PsiIrFileEntry.psiFile.text`)
     * 2. filesystem (`File(fileEntry.name).readText(UTF-8)`)
     *
     * 結果は文字列としてそのまま返す (改行コード変換はしない)。後段 ([SourceNormalizer.normalize])
     * が LF に正規化する。
     */
    fun loadFileText(file: IrFile): String? {
        // 第一選択: PSI が利用可能ならその text を使う (offset 整合が確実)
        (file.fileEntry as? PsiIrFileEntry)?.psiFile?.text?.let { return it }
        // フォールバック: file entry の name が file system 上のパスを指す場合はそれを読む
        val path = file.fileEntry.name
        val candidate = File(path)
        if (!candidate.isFile) return null
        return runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
    }

    /**
     * [fullText] の `[startOffset, endOffset)` 区間をそのまま substring する pure helper。
     *
     * 範囲が不正 (負値 / 反転 / fullText.length 超過) なら `null`。本関数は raw substring のみで、
     * dedent や annotation 行除外などの正規化は行わない (それらは [SourceNormalizer.normalize] の
     * 責務)。
     */
    fun substringOrNull(fullText: String, startOffset: Int, endOffset: Int): String? {
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
        if (endOffset > fullText.length) return null
        return fullText.substring(startOffset, endOffset)
    }
}
