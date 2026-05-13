package me.tbsten.capture.code.testapp.kmp103

/**
 * task-022 (ケース #103): mingwX64Main の actual 宣言。
 *
 * jvm 以外の target は本 ticket scope 外。annotation 無しで配置して compile success にする。
 */
@Suppress("unused")
internal actual fun kmpCase103_currentTimeMillis(): Long = 0L
