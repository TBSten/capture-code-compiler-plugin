package me.tbsten.capture.code.sample.jvm.cookbook

import me.tbsten.capture.code.sample.jvm.Snippet

// ============================================================================
// Case 01: 単純な marker + Source filler
//
// 最小ケース: marker を付けた関数の本文を Source として取得する。
// `capturedSources<Snippet>()` を呼ぶと、 同一 module 内の `@Snippet` で
// マークされた宣言が `List<Snippet>` として得られる。
// ============================================================================

@Snippet
internal fun greet(): String = "Hello!"

@Snippet
internal fun farewell(): String = "Goodbye!"
