package me.tbsten.capture.code.compat.k2000.userargs

import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

/**
 * marker annotation の filler 以外のパラメータ (= ユーザが call site で指定する parameter) を
 * IR 化するための helper (task-014)。
 *
 * filler 自動値埋め ([me.tbsten.capture.code.compat.k2000.filler.FillerBuilder]) との境界:
 * - filler 型 (`Source` / `SourceLocation` / `CaptureKind`) → `FillerBuilder` が値を生成する
 * - それ以外 (primitive / String / KClass / enum / 配列 / nested annotation) → 本 helper が
 *   call site の IR 式または marker class の `IrValueParameter.defaultValue` から値を取り出す
 *
 * ## 設計判断: FIR session storage は使わない
 *
 * 当初 (design §5 B-fir) は「FIR phase で `FirAnnotationCall.argumentMapping` を session-scoped
 * storage に push し、IR phase で読み出す」案だったが、Kotlin 2.0 では SOURCE retention の
 * annotation でも **IR まで `IrConstructorCall` が残る** (task-009 spike で式 annotation は
 * 残らないことを確認したが、宣言 annotation は task-005 / task-013 で残ることを確認済)。
 * このため、collector が marker `IrConstructorCall` を保持しておけば、rewriter は
 * `getValueArgument(i)` で直接ユーザの式を取り出せる。FIR storage を追加するより遥かに
 * シンプル。design §5 B-fir はあくまで「式 annotation 用 (task-017)」の経路として残し、
 * 宣言 annotation (本 ticket) では IR 直接ルートを採用する。
 *
 * ## deepCopy の必要性
 *
 * 取り出した `IrExpression` は元の declaration `annotations` 配下にぶら下がっている。
 * これを新しい `IrConstructorCall` (= `capturedSources<T>()` 書き換え結果の marker instance) に
 * `putValueArgument` で詰めると、**同じ IrElement が IR tree の 2 箇所に存在する状態** になり、
 * IR invariants 違反 (parent pointer の整合性が崩れる) になる。
 *
 * これを避けるため、すべて [deepCopyWithSymbols] でコピーした expression を渡す。コピーは
 * Kotlin 標準の `org.jetbrains.kotlin.ir.util.deepCopyWithSymbols` を使う (内部で
 * `DeepCopyIrTreeWithSymbols` を呼ぶ)。
 *
 * ## Default 値の伝搬
 *
 * call site で argument が省略されている場合 (`IrConstructorCall.getValueArgument(i) == null`)、
 * marker class の `IrValueParameter.defaultValue?.expression` を使う。これは marker class の
 * source で `val priority: Int = 0` のように書かれた `0` 等の expression。
 *
 * filler 型のパラメータも `Source = Source()` のように default 値を持つが、こちらは
 * `FillerBuilder` が plugin の自動値で **強制上書き** するので本 helper の対象外。
 */
internal object UserArgIrBuilder {

    /**
     * call site で指定された値か、marker class の default 値を deepCopy して返す。
     *
     * task-017 で [markerCall] を nullable に拡張。 EXPRESSION 起源 (式 annotation) では
     * task-009 spike (R1) より IR phase で marker `IrConstructorCall` が残らないため、
     * markerCall == null の場合は **直接** default 値経路を使う。 ユーザ定義 parameter の
     * **動的値** (primitive など) は別 builder ([UserArgPrimitiveIrBuilder]) で扱う想定だが、
     * 本 ticket scope では「ケース #7 / #67 = filler のみが入った marker」 を主にサポートするため、
     * 式起源で markerCall == null かつ default 値も無い場合は `null` を返して呼び出し側に
     * 委ねる (= putValueArgument スキップ → primary constructor の default で fill)。
     *
     * @param markerCall declaration に付けられた `@Marker(...)` の `IrConstructorCall`。
     *                   EXPRESSION 起源では `null`。
     * @param parameterIndex marker primary constructor における parameter の 0-based index
     * @param parameter marker primary constructor の対応する [IrValueParameter] (default 値の取得元)
     * @return ユーザが指定した IR 式 (deepCopy 済) または default 値の IR 式 (deepCopy 済)。
     *         どちらも取り出せない場合 (required パラメータが省略されているなど、本来 compile error
     *         になるべきケース) は `null`
     */
    fun buildOrDefault(
        markerCall: IrConstructorCall?,
        parameterIndex: Int,
        parameter: IrValueParameter,
    ): IrExpression? {
        if (markerCall != null) {
            val userExpr = markerCall.getValueArgument(parameterIndex)
            if (userExpr != null) return userExpr.deepCopyWithSymbols()
        }

        // call site で省略されている / EXPRESSION 起源で markerCall が無い
        // → marker class 側の default 値を使う
        val defaultExpr = parameter.defaultValue?.expression ?: return null
        return defaultExpr.deepCopyWithSymbols()
    }
}
