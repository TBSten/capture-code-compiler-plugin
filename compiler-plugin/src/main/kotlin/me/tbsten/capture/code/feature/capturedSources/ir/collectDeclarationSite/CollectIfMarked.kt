package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase

/**
 * 1 declaration から、 marker annotation が付いていれば marker ごとに 1 件ずつ [CollectedSite]
 * を生成して [sink] に追加する。 複数 marker 同時付与 (`@Foo @Bar fun x()`) は marker 数分発火。
 *
 * Phase 3a 移植元:
 * - `K200CapturedSourcesCollector.collectIfMarked(...)` (compat-k200, 同等は全 6 compat に存在)
 *
 * source 抽出は [extractDeclarationSource] で行い、 失敗 (file text 取得失敗 / offset 不正) の
 * 場合はその marker 1 件のみ skip する (他 marker の処理は継続)。
 *
 * `startLine` / `endLine` は `IrFileEntry.getLineNumber()` 由来 (0-based) なので **+1** で
 * 1-based に揃える (filler の design 値域に合わせる)。
 */
internal fun collectIfMarked(
    declaration: IrDeclarationBase,
    kind: CapturedSite.CaptureKind,
    context: CollectFileContext,
    sink: MutableList<CollectedSite>,
) {
    val markerAnnotations = declaration.annotations.markerAnnotations()
    if (markerAnnotations.isEmpty()) return
    val startLine = context.file.fileEntry.getLineNumber(declaration.startOffset) + 1
    val endLine = context.file.fileEntry.getLineNumber(declaration.endOffset) + 1
    for ((markerFqn, markerCall) in markerAnnotations) {
        val effective = context.effectiveConfigFor(markerFqn)
        val source = extractDeclarationSource(
            declaration = declaration,
            effective = effective,
            cachedFileText = context.cachedFileText(),
            site = context.site,
        ) ?: continue
        sink += CollectedSite(
            site = CapturedSite(
                markerFqn = markerFqn,
                source = source,
                kind = kind,
                packageFqn = context.packageFqn,
                filePath = context.filePath,
                startLine = startLine,
                endLine = endLine,
            ),
            markerCall = markerCall,
            effectiveConfig = effective,
        )
    }
}
