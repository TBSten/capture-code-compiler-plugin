package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.feature.capturedSources.CapturedSite

/**
 * `@file:Marker` で file 全体に付与された marker annotation を走査して [CollectedSite]
 * (kind = FILE) を生成し [sink] に追加する。
 *
 * Phase 3a 移植元:
 * - `K200CapturedSourcesCollector.collectFileAnnotations(...)`
 *
 * `IrFile.annotations` は IR walker の再帰経路では訪問されないため、 declaration 経路とは別に
 * 本 helper を呼ぶ必要がある (= [CollectDeclarationSite] の `collectInFile` から独立 entry で
 * call out)。
 *
 * source は file 全体テキスト → marker class declaration 除外 → file normalize の順で抽出する。
 * - `endLine` は `fileEntry.maxOffset` の line + 1 (`getLineNumber` は 0-based)
 * - `startLine` は **常に 1** (file 起源は file 先頭から)
 */
internal fun collectFileAnnotations(
    context: CollectFileContext,
    sink: MutableList<CollectedSite>,
) {
    val fileAnnotations = context.file.annotations.markerAnnotations()
    if (fileAnnotations.isEmpty()) return
    val endLine = context.file.fileEntry.getLineNumber(context.file.fileEntry.maxOffset) + 1
    for ((markerFqn, markerCall) in fileAnnotations) {
        val effective = context.effectiveConfigFor(markerFqn)
        val source = extractFileSource(
            file = context.file,
            effective = effective,
            cachedFileText = context.cachedFileText(),
        ) ?: continue
        sink += CollectedSite(
            site = CapturedSite(
                markerFqn = markerFqn,
                source = source,
                kind = CapturedSite.CaptureKind.FILE,
                packageFqn = context.packageFqn,
                filePath = context.filePath,
                startLine = 1,
                endLine = endLine,
            ),
            markerCall = markerCall,
            effectiveConfig = effective,
        )
    }
}
