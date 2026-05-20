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
 *
 * ## Preconditions
 *
 * Caller (= [CollectDeclarationSite.collectInFile]) は以下を保証する責務がある。 違反時は当該
 * marker 1 件のみ silent skip し、 他 marker / declaration 経路に影響を与えない設計のため、
 * `require(...)` での fail-fast は導入していない。
 *
 * - `context: CollectFileContext` は file 単位 immutable cache。
 * - `context.file.annotations` は IR resolution 完了済 (= `markerAnnotations()` 内で
 *   `IrConstructorCall.type.classFqName` が解決可能)。 typical root cause: caller が IR
 *   resolution 完了前の file を渡している (= phase 順序 bug)。
 * - `context.file.fileEntry.maxOffset` は file の最終 offset (IR API 仕様)。 0-based line 取得は
 *   `getLineNumber()` 経由で +1 して 1-based に揃える。
 * - [extractFileSource] で `cachedFileText` が `null` の場合は marker 1 件を silent skip
 *   (= LOGGING level の breadcrumb は extractFileSource 側で発火)。
 * - [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   は FIR phase 完了後の状態。 未登録の annotation は `markerAnnotations()` 内 filter で除外。
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
