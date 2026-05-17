package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * marker annotation の filler 以外のパラメータ (= ユーザが call site で指定する parameter) を
 * IR 化するための helper class。 task-120-B Phase 4b で concrete 化。
 *
 * 既存 `compat-kXXX/userargs/UserArgIrBuilder.kt` (`buildOrDefault`) を移植したもので、 IR
 * access (`getValueArgument` / `deepCopyWithSymbols`) は K2.4-RC で削除/`@DeprecatedForRemovalCompilerApi`
 * となっているため、 main bytecode から直接呼ぶと K2.4-RC runtime で NoSuchMethodError になる。
 * そのため [CompatContext.getCallValueArgument] / [CompatContext.deepCopyExpression] (Phase 2 で
 * 追加した SPI) 経由で drift 吸収する。
 *
 * ## 責務
 *
 * - markerCall (= declaration / file 起源で non-null) があれば
 *   [CompatContext.getCallValueArgument] でユーザの IR 式を取り出し、
 *   [CompatContext.deepCopyExpression] で deepCopy して返す
 * - markerCall が null (= EXPRESSION 起源) または argument が `null` (= 省略) なら、 marker class の
 *   `IrValueParameter.defaultValue?.expression` を deepCopy して返す
 * - default 値も無い (= required parameter 省略) なら `null` を返す。 上位 [BuildMarkerInstance]
 *   は putValueArgument を skip し、 primary constructor の default で fill する
 *
 * ## deepCopy の必要性
 *
 * 取り出した `IrExpression` は元の declaration `annotations` 配下にぶら下がっている。 これを新しい
 * `IrConstructorCall` (= `capturedSources<T>()` 書き換え結果の marker instance) に
 * `putValueArgument` で詰めると **同じ IrElement が IR tree の 2 箇所に存在する状態** になり、 IR
 * invariants 違反 (parent pointer の整合性が崩れる) になる。 これを避けるため、 すべて
 * [CompatContext.deepCopyExpression] でコピーした expression を渡す。
 *
 * ## 旧構造との関係
 *
 * 既存 `K{XXX}/userargs/UserArgIrBuilder.kt` (`buildOrDefault` 関数) は runtime path として並行
 * 存続する。 Phase 5 で `transformIr` を main 経由に切り替えた時点で本 class が runtime path
 * になり、 Phase 6 で旧 `UserArgIrBuilder` 削除予定。
 */
internal class BuildUserArg {

    /**
     * call site で指定された値か marker class の default 値を deepCopy して返す。
     *
     * @param markerCall declaration に付けられた `@Marker(...)` の `IrConstructorCall`。
     *   EXPRESSION 起源では `null`。
     * @param parameterIndex marker primary constructor における parameter の 0-based index
     * @param parameter marker primary constructor の対応する [IrValueParameter] (default 値の取得元)
     * @param compat IR access drift (`getValueArgument` / `deepCopyWithSymbols` の K2.4-RC 削除) を
     *   吸収するための SPI
     * @return ユーザが指定した IR 式 (deepCopy 済) または default 値の IR 式 (deepCopy 済)。
     *   どちらも取り出せない場合 (required パラメータが省略されているなど、 本来 compile error に
     *   なるべきケース) は `null`
     */
    internal operator fun invoke(
        markerCall: IrConstructorCall?,
        parameterIndex: Int,
        parameter: IrValueParameter,
        compat: CompatContext,
    ): IrExpression? {
        if (markerCall != null) {
            val userExpr = compat.getCallValueArgument(markerCall, parameterIndex)
            if (userExpr != null) return compat.deepCopyExpression(userExpr)
        }

        // call site で省略されている / EXPRESSION 起源で markerCall が無い
        // → marker class 側の default 値を使う
        val defaultExpr = parameter.defaultValue?.expression ?: return null
        return compat.deepCopyExpression(defaultExpr)
    }
}
