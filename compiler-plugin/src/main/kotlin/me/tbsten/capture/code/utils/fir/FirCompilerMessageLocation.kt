package me.tbsten.capture.code.utils.fir

import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation

/**
 * [KtSourceElement] から [CompilerMessageLocation] を組み立てる汎用 helper。
 *
 * FIR phase から `MessageCollector.report(severity, message, location)` 経由で診断を出す際に、
 * source element の PSI 情報を location (file path + line/column) に変換する。
 * capture-code 固有の domain 知識は持たない (どの plugin でも再利用可能な純粋変換)。
 *
 * 解決順:
 *
 * 1. file path: PSI virtualFile の絶対パス → [fallbackFilePath] → どちらも無ければ `null`
 *    (= location なし報告に degrade)
 * 2. line/column: PSI Document の offset → line 変換。 Document が取れない / offset が不正な
 *    場合は `-1` (= line/column 未指定) に degrade
 *
 * PSI 経由の path 解決 (`KtPsiSourceElement.psi.containingFile.virtualFile.path`) と
 * offset ベースの座標計算はいずれも intellij-core の安定 API のみを使う
 * (main module の K2.0 baseline compile / K2.0-K2.4 runtime で drift しない範囲)。
 *
 * @param source location の元になる source element。 `null` / PSI 無し (light tree) でも安全
 * @param fallbackFilePath PSI から file path が取れない場合に使う path
 *   (例: `CompatContext.containingFilePathOf(context)`)
 * @return 組み立てた location。 file path が全く解決できない場合は `null`
 */
internal fun compilerMessageLocationOf(
    source: KtSourceElement?,
    fallbackFilePath: String? = null,
): CompilerMessageLocation? {
    val psiFile = (source as? KtPsiSourceElement)?.psi?.containingFile
    val path = psiFile?.virtualFile?.path ?: fallbackFilePath ?: return null

    val lineAndColumn = runCatching {
        val document = psiFile?.viewProvider?.document ?: return@runCatching null
        val offset = source?.startOffset?.takeIf { it in 0..document.textLength } ?: return@runCatching null
        val line = document.getLineNumber(offset)
        val column = offset - document.getLineStartOffset(line)
        (line + 1) to (column + 1)
    }.getOrNull()

    return CompilerMessageLocation.create(
        path,
        lineAndColumn?.first ?: -1,
        lineAndColumn?.second ?: -1,
        null,
    )
}
