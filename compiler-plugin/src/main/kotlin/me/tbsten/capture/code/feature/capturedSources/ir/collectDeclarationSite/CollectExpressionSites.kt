package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * FIR session storage ([CaptureCodeExpressionSiteRegistry]) に push された式 annotation site
 * のうち、 当該 file にマッチするものを [CollectedSite] (kind = EXPRESSION) に変換して
 * [sink] に追加する。
 *
 * Phase 3a 移植元:
 * - `K200CapturedSourcesCollector.collectExpressionSites(...)`
 *
 * design §5 Logic B-fir の **IR phase 側読み出し**:
 * 1. registry にある全 markerFqn / filePath の組み合わせを走査
 * 2. file path が当該 file の `fileEntry.name` と一致 (絶対パス完全一致 / 末尾 leaf 一致) する
 *    site のみ採用
 * 3. site の `startOffset..endOffset` から `extractExpressionSource` で raw text を抽出して
 *    paren strip + normalize
 * 4. [CollectedSite] (kind = EXPRESSION) として登録
 *
 * `markerCall` は EXPRESSION 起源では `null`。 user args は FIR から push された `userArgs`
 * (= primitive / enum FqN) をそのまま運ぶ (IR 化は rewriter 側 = Phase 4 で行う)。
 *
 * FIR checker は **すべての expression annotation** を push してくるため、 IR phase 側で
 * [CaptureCodeMarkerRegistry.isMarker] による filter を行う。
 */
internal fun collectExpressionSites(
    context: CollectFileContext,
    sink: MutableList<CollectedSite>,
) {
    if (CaptureCodeExpressionSiteRegistry.allSites.isEmpty()) return
    val fileText = context.cachedFileText() ?: return
    val matchingSites = CaptureCodeExpressionSiteRegistry.allSites
        .asSequence()
        .filter { CaptureCodeMarkerRegistry.isMarker(it.markerFqn) }
        .filter { context.site.matchesFile(it, context.filePath) }
        .sortedBy { it.startOffset }
        .toList()
    for (site in matchingSites) {
        val effective = context.effectiveConfigFor(site.markerFqn)
        val source = extractExpressionSource(
            fullText = fileText,
            startOffset = site.startOffset,
            endOffset = site.endOffset,
            effective = effective,
            site = context.site,
        ) ?: continue
        val startLine = context.file.fileEntry.getLineNumber(site.startOffset) + 1
        val endLine = context.file.fileEntry.getLineNumber(site.endOffset) + 1
        sink += CollectedSite(
            site = CapturedSite(
                markerFqn = site.markerFqn,
                source = source,
                kind = CapturedSite.CaptureKind.EXPRESSION,
                packageFqn = context.packageFqn,
                filePath = context.filePath,
                startLine = startLine,
                endLine = endLine,
            ),
            markerCall = null,
            expressionUserArgs = site.userArgs,
            effectiveConfig = effective,
        )
    }
}
