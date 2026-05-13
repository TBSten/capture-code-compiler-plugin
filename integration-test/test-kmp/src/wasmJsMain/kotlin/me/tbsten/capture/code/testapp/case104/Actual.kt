package me.tbsten.capture.code.testapp.case104

/**
 * ケース #104: wasmJs 用 actual (annotation 付き)。
 *
 * wasmJs target compilation で可視。本 ticket では assemble が通ることのみ
 * 保証し、IR transformer 実機検証は task-025 に委譲。
 */
@Platform_KmpCase104
internal actual fun kmpCase104_platformName(): String = "wasmJs"
