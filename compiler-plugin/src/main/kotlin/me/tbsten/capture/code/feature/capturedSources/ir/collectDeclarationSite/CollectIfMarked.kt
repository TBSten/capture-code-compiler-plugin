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
 *
 * Kotlin 2.2.20+ では `IrDeclaration.startOffset` が宣言キーワード (`val` / `fun` …) を指し、
 * `@Marker` 行や `internal` modifier 行を含まないように変わった (drift D-IR-34)。 K200/K210
 * baseline では annotation / modifier 行を含む位置を指していたため、 そのまま `getLineNumber`
 * すると `SourceLocation.startLine` が cell 間で 1 行ずれて返る (Charter 2 detection)。
 * [CollectDeclarationSite.expandStartToCoverModifierAndAnnotationLines] で baseline 同等まで
 * 戻してから line 計算する。 fullText 取得失敗時は補正なし fallback (= 旧 drift を残すが
 * crash しない)。
 *
 * ## Preconditions
 *
 * Caller (= [CollectDeclarationSite.collectInFile] 内 declaration walk callback) は以下を保証
 * する責務がある。 違反時は当該 marker 1 件のみ silent skip し、 他 marker / 他 declaration 経路に
 * 影響を与えない設計のため、 `require(...)` での fail-fast は導入していない。
 *
 * - `declaration: IrDeclarationBase` は IR resolution 完了済の class / function / property /
 *   typealias (= `compat.walkIrFileDeclarations` callback 経由でのみ呼ばれる)。 `annotations`
 *   は resolved (= `IrConstructorCall.type.classFqName` が解決可能)。
 * - `kind: CapturedSite.CaptureKind` は caller が `IrClass.kind` (CLASS / OBJECT 等) から
 *   mapping したもの (CLASS / OBJECT / FUNCTION / PROPERTY / TYPEALIAS のいずれか)。 EXPRESSION
 *   / FILE は本経路では渡らない (= caller の switch case で別経路に dispatch)。
 * - `context: CollectFileContext` は declaration が属する IrFile に紐づく immutable cache。
 * - `declaration.startOffset` / `declaration.endOffset` は file 内 offset (IR API 仕様)。
 *   UNDEFINED (-1) や逆転 offset は [extractDeclarationSource] 内 `ExtractSourceText` で
 *   silent skip される。
 * - [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   は FIR phase 完了後の状態。 未登録の annotation は `markerAnnotations()` 内 filter で除外。
 */
internal fun collectIfMarked(
    declaration: IrDeclarationBase,
    kind: CapturedSite.CaptureKind,
    context: CollectFileContext,
    sink: MutableList<CollectedSite>,
) {
    val markerAnnotations = declaration.annotations.markerAnnotations()
    if (markerAnnotations.isEmpty()) return
    val rawStartOffset = declaration.startOffset
    val cachedFullText = context.cachedFileText()
    val effectiveStartOffset = if (cachedFullText != null && rawStartOffset >= 0) {
        context.site.expandStartToCoverModifierAndAnnotationLines(cachedFullText, rawStartOffset)
    } else {
        rawStartOffset
    }
    val startLine = context.file.fileEntry.getLineNumber(effectiveStartOffset) + 1
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
