package me.tbsten.capture.code.testapp.case104

/**
 * ケース #104: jvm 用 actual (annotation 付き)。
 *
 * jvm target compilation でのみ可視で、`capturedSources<Platform_KmpCase104>()` を
 * jvm 上で呼ぶと本 actual のソース 1 件のみを返す想定。
 */
@Platform_KmpCase104
internal actual fun kmpCase104_platformName(): String = "JVM"
