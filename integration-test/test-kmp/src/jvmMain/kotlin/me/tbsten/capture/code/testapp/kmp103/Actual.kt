package me.tbsten.capture.code.testapp.kmp103

/**
 * task-022 (ケース #103): jvmMain の actual 宣言。`@Platform_KmpCase103` も付与する。
 *
 * このファイルが jvm target でキャプチャされる 2 件目 (expect の commonMain 側と独立) になる。
 */
@Platform_KmpCase103
internal actual fun kmpCase103_currentTimeMillis(): Long = System.currentTimeMillis()
