package me.tbsten.capture.code.feature.capturedSources.ir.normalize

import me.tbsten.capture.code.CaptureCodePluginConfig

/**
 * [CaptureCodePluginConfig] (SSOT) から各起源別 [NormalizeOptions] へ投影する bridge 関数群。
 *
 * [CaptureCodePluginConfig] の `dedent` / `includeAnnotationLines` / `includeImports` 等の flag は、
 * 起源 (declaration / file / expression) ごとに違う形で [NormalizeOptions] にマッピングされる。
 * 本ファイルはそのマッピング規則の **唯一の場所** (SSOT)。
 *
 * 各 compat-kXXXX 実装はここで定義された extension を呼ぶことで、同じ規則を共有する。
 */

/**
 * declaration 起源 (property / class / function / typealias / object) のための [NormalizeOptions] を返す。
 *
 * - `dedent` は config のまま反映
 * - `trimBlankEdges` は常に `true` (design §5 Logic D 仕様)
 * - `stripPackageAndImport` は declaration には不要なので常に `false`
 * - `stripLeadingAnnotationLines` は **`false`**。declaration 起源では marker 行の除去は
 *   collector 側 (`skipLeadingMarkerAnnotations` + markerRanges drop) が担っており、
 *   後段の normalize で重ねて strip する必要はない。`includeAnnotationLines = true` の場合は
 *   collector 側 (`extractDeclarationSource`) が marker 行の skip / drop 自体をスキップするため、
 *   `@Marker` 行がそのまま capture に含まれる。 非 marker annotation 行 (`@JvmInline` 等) は
 *   semantic に意味を持つため、 この flag と無関係に常に capture に残る。
 */
public fun CaptureCodePluginConfig.toDeclarationNormalizeOptions(): NormalizeOptions =
    NormalizeOptions(
        dedent = dedent,
        trimBlankEdges = true,
        stripPackageAndImport = false,
        stripLeadingAnnotationLines = false,
        stripKdoc = !includeKdoc,
    )

/**
 * file 起源 (`@file:Marker`) のための [NormalizeOptions] を返す。
 *
 * - `stripPackageAndImport` は `!includeImports` で決定
 * - その他は declaration と同じ
 */
public fun CaptureCodePluginConfig.toFileNormalizeOptions(): NormalizeOptions =
    NormalizeOptions(
        dedent = dedent,
        trimBlankEdges = true,
        stripPackageAndImport = !includeImports,
        stripLeadingAnnotationLines = !includeAnnotationLines,
        stripKdoc = !includeKdoc,
    )

/**
 * 式起源 (`@Marker (expr)`) のための [NormalizeOptions] を返す。
 *
 * - `dedentIgnoreFirstLine` は **式起源のみ `true`** (bug-006)。 式の抽出 text は 1 行目が
 *   行の途中から始まり得てインデント情報を持たないため、 最小インデント幅の計算から
 *   1 行目を除外する ([dedentLines] の KDoc 参照)。
 */
public fun CaptureCodePluginConfig.toExpressionNormalizeOptions(): NormalizeOptions =
    NormalizeOptions(
        dedent = dedent,
        dedentIgnoreFirstLine = true,
        trimBlankEdges = true,
        stripPackageAndImport = false,
        stripLeadingAnnotationLines = false,
    )
