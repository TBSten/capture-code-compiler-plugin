package me.tbsten.capture.code.testapp.kmp103

/**
 * task-022 (ケース #103): jsMain の actual 宣言。
 *
 * 本 ticket は **jvm target のみ** で expect + actual の 2 件キャプチャを検証する。
 * jsMain 側の actual には annotation を付けない (= ケース #104 寄りになるため scope 外)。
 * compile success にするためだけの最小実装。
 */
@Suppress("unused")
internal actual fun kmpCase103_currentTimeMillis(): Long =
    kotlin.js.Date.now().toLong()
