package me.tbsten.capture.code.sample

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

/**
 * task-040 fixture: ケース #1 相当の最小 marker annotation。
 *
 * Source filler のみを宣言。 Use site で `@Snippet` を付けた宣言の本文 (source string) が
 * compiler plugin によって埋められる。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippet(
    val source: Source = Source(),
)
