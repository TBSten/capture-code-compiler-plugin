package me.tbsten.capture.code.feature.captured_sources.normalize

import me.tbsten.capture.code.CaptureCodePluginConfig

/**
 * [CaptureCodePluginConfig] (SSOT) から各起源別 [NormalizeOptions] へ投影する bridge 関数群。
 *
 * task-018 で導入された [CaptureCodePluginConfig] の `dedent` / `includeAnnotationLines` /
 * `includeImports` 等の flag は、起源 (declaration / file / expression) ごとに違う形で
 * [NormalizeOptions] にマッピングされる。本ファイルはそのマッピング規則の **唯一の場所** (SSOT)。
 *
 * 各 compat-kXXXX 実装はここで定義された extension を呼ぶことで、同じ規則を共有する。
 *
 * 参照: task-015 完了メモ §「CaptureCodePluginConfig → NormalizeOptions の glue」
 */

/**
 * declaration 起源 (property / class / function / typealias / object) のための [NormalizeOptions] を返す。
 *
 * - `dedent` は config のまま反映
 * - `trimBlankEdges` は常に `true` (design §5 Logic D 仕様)
 * - `stripPackageAndImport` は declaration には不要なので常に `false`
 * - `stripLeadingAnnotationLines` は **`false`**。compat-kXXXX 側で IR offset を annotation
 *   行を含まないよう既に補正している (Kotlin 2.0.0 の `skipLeadingAnnotationLines`) ため、
 *   後段の normalize で重ねて strip する必要はない。`includeAnnotationLines = true` の場合は
 *   collector 側で offset 補正をスキップする (task-018 でも未実装、polish item)。
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
 * file 起源 (`@file:Marker`) のための [NormalizeOptions] を返す。task-016 で利用予定。
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
 * 式起源 (`@Marker (expr)`) のための [NormalizeOptions] を返す。task-017 で利用予定。
 */
public fun CaptureCodePluginConfig.toExpressionNormalizeOptions(): NormalizeOptions =
    NormalizeOptions(
        dedent = dedent,
        trimBlankEdges = true,
        stripPackageAndImport = false,
        stripLeadingAnnotationLines = false,
    )
