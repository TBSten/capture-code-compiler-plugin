@file:Suppress("unused")

package me.tbsten.capture.code.sample.jvm.cookbook

import me.tbsten.capture.code.sample.jvm.TypeDoc

// ============================================================================
// Case 04: 全宣言ターゲット (class / object / function / typealias)
//
// `@TypeDoc` は AnnotationTarget を class / function / property / typealias と
// 広めに宣言してあるため、 同じ marker で複数種類の宣言をマークできる。
// `capturedSources<TypeDoc>()` は種類を問わず全てを集める (`kind` filler で
// 区別可能)。
// ============================================================================

/** ユーザを表す data class。 */
@TypeDoc
internal data class User(val id: Long, val name: String)

/** singleton としての設定オブジェクト。 */
@TypeDoc
internal object AppConfig {
    val version: String = "0.1.0"
}

/** 簡単な関数宣言。 */
@TypeDoc
internal fun describe(user: User): String = "User#${user.id}=${user.name}"

/** typealias 宣言。 */
@TypeDoc
internal typealias UserId = Long
