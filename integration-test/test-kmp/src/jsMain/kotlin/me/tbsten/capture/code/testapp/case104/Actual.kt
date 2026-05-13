package me.tbsten.capture.code.testapp.case104

/**
 * ケース #104: js 用 actual (annotation 付き)。
 *
 * js target compilation で可視。target 別の独立性 (各 target で別の actual がキャプチャ
 * される) を保証する。E2E 検証は task-025 に委譲。
 */
@Platform_KmpCase104
internal actual fun kmpCase104_platformName(): String = "JS"
