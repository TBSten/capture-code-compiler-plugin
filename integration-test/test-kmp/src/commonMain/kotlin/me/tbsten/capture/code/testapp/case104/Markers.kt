package me.tbsten.capture.code.testapp.case104

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

/**
 * ケース #104: actual のみに annotation (expect は無印)
 *
 * commonMain に marker を置き、`expect` 側には annotation を付けない。
 * `@Platform_KmpCase104` は各 target の `actual` 宣言にのみ付く。compiler-plugin-design.md
 * §7.6 に従い、`capturedSources<T>()` は対象 target compilation で見える actual の
 * 1 件だけをキャプチャする。各 target ごとに actual が違うので、target ごとに違う 1 件が
 * 含まれることになる。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Platform_KmpCase104(val source: Source = Source())
