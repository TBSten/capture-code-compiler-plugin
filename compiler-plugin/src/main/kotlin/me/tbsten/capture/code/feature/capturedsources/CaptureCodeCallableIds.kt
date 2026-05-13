package me.tbsten.capture.code.feature.capturedsources

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * `capturedSources<T>()` 関数 (`me.tbsten.capture.code.capturedSources`) の identity を表す SSOT。
 *
 * FIR checker (Logic G: `CapturedSourcesCallChecker`) と IR transformer (Logic H:
 * `K2000CapturedSourcesTransformer` 等の compat 実装) の両方が **同じ 1 つの定義** を参照することで、
 * パッケージ名・関数名のミスマッチによる「checker は走るが書き換えは走らない / 逆」のバグを防ぐ。
 *
 * ## なぜ `feature/captured-sources/` 直下か (ticket-011 の配置判断)
 *
 * ticket-011 では候補として `error/` または `compat/` 直下が挙げられているが、本ファイルは
 * 「診断メッセージ」でも「Kotlin バージョン互換 layer」でもなく、`capturedSources<T>()` という
 * **`captured-sources` feature の symbol identity** である。design §8.5 の
 * 「feature 内は 1 ディレクトリで完結させる」方針に従い、feature ディレクトリ直下に置く。
 *
 * Logic H 側 (現状は `compat-k2000` module の `K2000CapturedSourcesRewriter.CAPTURED_SOURCES_FQN`)
 * は別 module のため本 SSOT を直接 import できない。将来 compat を main module から呼ぶ wiring に
 * 切り替えるか、本 constants を compat module 側に複製する方針を取る。それまでは
 * 「FqN 文字列が一致する」ことを test (`CapturedSourcesCallCheckerTest`) で間接的に担保する。
 */
internal object CaptureCodeCallableIds {

    /** `capturedSources<T>()` の package (= `me.tbsten.capture.code`)。 */
    val packageFqName: FqName = FqName("me.tbsten.capture.code")

    /** `capturedSources` という関数名。 */
    val capturedSourcesName: Name = Name.identifier("capturedSources")

    /** `me.tbsten.capture.code.capturedSources` の `CallableId`。 */
    val capturedSources: CallableId = CallableId(
        packageName = packageFqName,
        callableName = capturedSourcesName,
    )
}
