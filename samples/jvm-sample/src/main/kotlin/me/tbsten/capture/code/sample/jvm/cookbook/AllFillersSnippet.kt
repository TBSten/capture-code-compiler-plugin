package me.tbsten.capture.code.sample.jvm.cookbook

import me.tbsten.capture.code.sample.jvm.DetailedSnippet

// ============================================================================
// 3 種類の filler を同時に使うサンプル (Source / SourceLocation / CaptureKind)
//
// `@DetailedSnippet` には Source / SourceLocation / CaptureKind の全 filler が
// 宣言されているため、 plugin はそれぞれを埋める。
//   - source.value           : 関数本文 (KDoc 等は dedent ルール適用後)
//   - location.packageName   : 宣言の package 名
//   - location.filePath      : 宣言の絶対パス
//   - location.startLine     : 宣言の開始行
//   - location.endLine       : 宣言の終了行
//   - kind.value             : Kind.FUNCTION
// ============================================================================

@DetailedSnippet
internal fun addOne(x: Int): Int = x + 1

@DetailedSnippet
internal val pi: Double = 3.14159
