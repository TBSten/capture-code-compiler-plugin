package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.ir.extractSourceText.ExtractSourceText
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.NormalizeSource
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toDeclarationNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toExpressionNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toFileNormalizeOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * declaration 1 件分の source 抽出 + 正規化。
 *
 * 処理ステップ:
 * 1. file text を取得 (取得不可なら `null`)
 * 2. declaration の `startOffset..endOffset` 範囲 + offset validity 確認
 * 3. `includeKdoc = true` なら直前 KDoc を抽出 ([CollectDeclarationSite.extractKdocPrefix])
 * 4. 先頭 `@Marker` 行を skip ([CollectDeclarationSite.skipLeadingAnnotationLines])
 * 5. raw substring 抽出 ([ExtractSourceText])
 * 6. KDoc prefix と body を結合
 * 7. [NormalizeSource] で dedent / blank trim 等を適用 ([toDeclarationNormalizeOptions])
 *
 * 失敗条件 (`null` 返却):
 * - file text が読めない
 * - declaration の offset が UNDEFINED (-1) または不正
 * - offset が file text の範囲外
 */
internal fun extractDeclarationSource(
    declaration: IrDeclarationBase,
    effective: CaptureCodePluginConfig,
    cachedFileText: String?,
    site: CollectDeclarationSite,
): String? {
    val fullText = cachedFileText ?: return null
    val startOffset = declaration.startOffset
    val endOffset = declaration.endOffset
    if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
    if (endOffset > fullText.length) return null
    // `includeKdoc = true` (デフォルト) の場合、 declaration の startOffset の
    // 直前にある KDoc を別途抽出する。 KDoc は `@Marker` 行より手前にあるため、
    // 単純に startOffset を前方拡張すると `@Marker` 行が skip されない問題がある
    // (skipLeadingAnnotationLines は連続する `@` 行のみ skip するため、 KDoc 行で中断する)。
    // そこで KDoc 抽出と body 抽出を **分離** し、 後で連結する戦略を採る。
    val kdocPrefix = if (effective.includeKdoc) site.extractKdocPrefix(fullText, startOffset) else ""
    val rawStart = site.skipLeadingAnnotationLines(fullText, startOffset, endOffset, markerSimpleNames())
    val rawBody = ExtractSourceText()(fullText, rawStart, endOffset) ?: return null
    val rawText = if (kdocPrefix.isNotEmpty()) kdocPrefix + "\n" + rawBody else rawBody
    return NormalizeSource()(rawText, effective.toDeclarationNormalizeOptions())
}

/**
 * file 全体テキスト → marker class declaration 除外 → file normalize の順で source を抽出する。
 *
 * marker class 自身は `@CaptureCode` メタ annotation 付きの annotation class 定義であり、
 * file 起源 capture の **対象** ではない。 marker class の declaration を file source から
 * drop することで、 ユーザ視点で「キャプチャされるべきコード」だけが残るようにする。
 */
internal fun extractFileSource(
    file: IrFile,
    effective: CaptureCodePluginConfig,
    cachedFileText: String?,
): String? {
    val fullText = cachedFileText ?: return null
    val withoutMarkers = stripMarkerClassDeclarations(file, fullText)
    return NormalizeSource()(withoutMarkers, effective.toFileNormalizeOptions())
}

/**
 * expression annotation 起源の raw 抽出 + 正規化。
 *
 * FIR session が push する `(startOffset, endOffset)` は対象 expression の source range。
 * 抽出後に [CollectDeclarationSite.stripSurroundingParens] で 両端 `(` `)` を strip し、
 * [toExpressionNormalizeOptions] で expression normalize を適用。
 */
internal fun extractExpressionSource(
    fullText: String,
    startOffset: Int,
    endOffset: Int,
    effective: CaptureCodePluginConfig,
    site: CollectDeclarationSite,
): String? {
    val raw = ExtractSourceText()(fullText, startOffset, endOffset) ?: return null
    val stripped = site.stripSurroundingParens(raw)
    return NormalizeSource()(stripped, effective.toExpressionNormalizeOptions())
}

/**
 * raw file text から、 本 [file] 内に定義された **marker class declaration** (= `@CaptureCode`
 * メタ付き annotation class) の `startOffset..endOffset` 範囲を drop した文字列を返す。
 *
 * marker class が複数ある場合は **降順** (`endOffset` の大きい順) に drop することで、
 * 早い range の drop が後の range の offset を invalid にしない (raw text の offset と
 * marker class の startOffset は同じ座標系)。
 *
 * marker class declaration の startOffset は Kotlin 2.0.0 の IR では先頭 `@Marker` 行を
 * 含む可能性があるが、 本関数は **既知の marker registry に登録された** annotation class
 * のみを対象とするため、 drop 範囲が広めでも問題ない (誤って削れる場合もない)。
 */
internal fun stripMarkerClassDeclarations(file: IrFile, text: String): String {
    val markerRanges = file.declarations
        .filterIsInstance<IrClass>()
        .filter { irClass ->
            val fqn = irClass.fqNameWhenAvailable?.asString() ?: return@filter false
            CaptureCodeMarkerRegistry.isMarker(fqn)
        }
        .mapNotNull { irClass ->
            val startOffset = irClass.startOffset
            val endOffset = irClass.endOffset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) null
            else if (endOffset > text.length) null
            else startOffset..endOffset
        }
        .sortedByDescending { it.last }
    if (markerRanges.isEmpty()) return text
    val builder = StringBuilder(text)
    for (range in markerRanges) {
        builder.delete(range.first, range.last)
    }
    return builder.toString()
}

/**
 * marker registry から「marker FqN の simpleName 集合 (= class 名のみ抜き出したもの)」を返す。
 *
 * 各 collect 経路で `skipLeadingAnnotationLines` に渡す `markerSimpleNames` 引数のため、
 * 都度計算 (= 1 declaration 1 回呼び出し) で問題ない (marker 数は通常 数件 〜 十数件)。
 */
internal fun markerSimpleNames(): Set<String> =
    CaptureCodeMarkerRegistry.markerFqns.mapTo(mutableSetOf()) { it.substringAfterLast('.') }
