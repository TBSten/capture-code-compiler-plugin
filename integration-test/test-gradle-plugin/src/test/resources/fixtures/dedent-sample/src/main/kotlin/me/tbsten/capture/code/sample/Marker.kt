package me.tbsten.capture.code.sample

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

/**
 * dedent option E2E 用 marker。
 *
 * `:integration-test:test-jvm:DslOptionsTest` 由来の以下 2 ケースに対応:
 *   - 「dedent=true (デフォルト) ではインデントが除去される」
 *   - 「dedent=false ではインデントが保持される」
 *
 * Use site (`Usage.kt`) は **意図的にネスト** された関数を 1 つだけ宣言し、
 * dedent on/off で出力 source string がどう変わるかを差分検証する。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class IndentedMarker(
    val source: Source = Source(),
)
