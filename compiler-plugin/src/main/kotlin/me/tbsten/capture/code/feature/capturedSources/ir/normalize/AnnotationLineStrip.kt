package me.tbsten.capture.code.feature.capturedSources.ir.normalize

/**
 * declaration 起源の正規化で、先頭に残った `@Marker` annotation 行を除外する pure function。
 *
 * 設計メモ:
 * - design §7.2 によれば、宣言の `startOffset` に先頭 `@Marker` 行が含まれるかは
 *   Kotlin バージョン依存である。
 * - 現状の compat 実装 (Kotlin 2.0.x / 2.1.x) では、IR offset 範囲には
 *   annotation 行が含まれない前提で実装しているため通常はこの関数は no-op になる。
 * - 万一含まれてしまった場合の保険として、先頭行が `@` で始まる行群を drop する。
 *   ただし KDoc 行 (`/**` / ` *` / `*/`) や `// comment` は **除外しない**。
 *
 * アルゴリズム:
 * - 先頭から見て、trim 後が `@` で始まる行と、その continuation (Kotlin の annotation は
 *   多行に渡る場合がある: `@Foo(\n    bar = 1\n)`) を一括 drop する想定だが、
 *   continuation 判定は構文情報がないため厳密には不可能。本実装では「`@` で始まる行を
 *   1 行ずつ drop し、それに続く blank 行も連動 drop する」シンプルな挙動とする。
 * - 最初の non-annotation / non-blank 行に到達したら以降はそのまま保持。
 *
 * 注意: この関数はあくまで保険であり、通常は IR offset 計算側で annotation を含まないよう
 * 制御することが望ましい (`CaptureCodePluginConfig.includeAnnotationLines` DSL option を参照)。
 */
public fun stripLeadingAnnotationLines(lines: List<String>): List<String> {
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trimStart()
        val isAnnotation = trimmed.startsWith("@")
        val isBlank = lines[i].isBlank()
        if (!isAnnotation && !isBlank) break
        i++
    }
    return if (i == 0) lines else lines.subList(i, lines.size)
}
