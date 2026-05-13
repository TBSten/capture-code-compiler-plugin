package me.tbsten.capture.code.testapp

/**
 * appleMain (ios + macos) intermediate source set placeholder.
 *
 * task-019 (KMP sample project skeleton) で配置。`applyDefaultHierarchyTemplate()`
 * によって ios* / macos* leaf target に共有される。Apple native target は Xcode
 * 必須のため `enableAppleTargets=true` Gradle property で opt-in する。デフォルト
 * disable では本 source set は build に含まれない。
 *
 * 各 target 別の use site / actual 宣言 / hierarchy 検証は後続 task (task-020 ~
 * task-024) で追加される。
 */
internal const val APPLE_MAIN_MARKER: String = "apple"
