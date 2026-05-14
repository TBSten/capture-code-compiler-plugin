@file:me.tbsten.capture.code.sample.jvm.FileTopic(
    topic = "fizzbuzz",
    // ↑ user-defined parameter は手動で指定
)

package me.tbsten.capture.code.sample.jvm.cookbook

// ============================================================================
// Case 05: file annotation
//
// `@file:FileTopic(...)` のように **file-level annotation** に marker を付ける
// と、 plugin は **そのファイル全体** の source code を Source filler に埋める。
// API 仕様書 / DSL カタログ / 教育用 snippet など、 「ファイル単位」 の説明物を
// 集めたいときに有用。
//
// 注: file annotation はパッケージ宣言より前に書く必要がある。
// ============================================================================

// このファイル内に通常の宣言を置いておく — file annotation はファイル全体の
// source を取得するので、 ここに書いた関数の本体も Source.value に含まれる。
internal fun fizzbuzz(n: Int): String = when {
    n % 15 == 0 -> "FizzBuzz"
    n % 3 == 0 -> "Fizz"
    n % 5 == 0 -> "Buzz"
    else -> n.toString()
}
