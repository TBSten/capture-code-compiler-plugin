package me.tbsten.capture.code.feature.capturedsources

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * `capturedSources<T>()` 関数 (`me.tbsten.capture.code.capturedSources`) の identity を表す SSOT。
 *
 * FIR checker (Logic G: `CapturedSourcesCallChecker`) と IR transformer (Logic H:
 * `K200CapturedSourcesTransformer` 等の compat 実装) の両方が **同じ 1 つの定義** を参照することで、
 * パッケージ名・関数名のミスマッチによる「checker は走るが書き換えは走らない / 逆」のバグを防ぐ。
 *
 * **配置**: task-072 で `:compiler-plugin` main module から `:compiler-plugin:compat` に移動。
 * Logic G の FIR checker class が compat-kXXX layer で実装されるため、 本 SSOT も共有モジュールに昇格した。
 */
public object CaptureCodeCallableIds {

    /** `capturedSources<T>()` の package (= `me.tbsten.capture.code`)。 */
    public val packageFqName: FqName = FqName("me.tbsten.capture.code")

    /** `capturedSources` という関数名。 */
    public val capturedSourcesName: Name = Name.identifier("capturedSources")

    /** `me.tbsten.capture.code.capturedSources` の `CallableId`。 */
    public val capturedSources: CallableId = CallableId(
        packageName = packageFqName,
        callableName = capturedSourcesName,
    )
}
