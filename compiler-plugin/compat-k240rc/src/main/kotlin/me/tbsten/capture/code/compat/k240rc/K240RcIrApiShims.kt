// task-076: Kotlin 2.4.0-RC で IrMemberAccessExpression.putValueArgument(Int, IrExpression?) /
// putTypeArgument(Int, IrType?) / getValueArgument(Int) / getTypeArgument(Int) が削除された。
// 代わりに `arguments: MutableList<IrExpression?>` (ValueArgumentsList) と
// `typeArguments: List<IrType>` が露出している。
//
// 本 file は **2.4.0-RC native API を 旧 API 風に呼び出すための内部 shim** であり、
// IrMemberAccessExpression / IrFunctionAccessExpression に対する safe set / get を提供する。
// 旧 API の semantics (= 指定 index の slot に書き込み、 既存 size 不足なら null 埋めで拡張) を
// なるべく忠実に再現する。
@file:Suppress("UNCHECKED_CAST")

package me.tbsten.capture.code.compat.k240rc

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType

/**
 * `expr.arguments[index] = value` 相当。 list が足りない場合は null 埋めで拡張する。
 *
 * 2.4.0-RC の `arguments` は `ArrayList<IrExpression?>` を継承する `ValueArgumentsList`。
 * IrConstructorCallImpl.fromSymbolOwner 等の factory は **空 list を初期化する** (引数を後で
 * put する前提)。 そのため、 旧 `putValueArgument(index, value)` の semantics を維持するには、
 * 既存 size が index 以下なら null padding してから set する必要がある。
 */
internal fun <S : IrSymbol> IrMemberAccessExpression<S>.putArgumentSafe(
    index: Int,
    value: IrExpression?,
) {
    @Suppress("UNCHECKED_CAST")
    val list = arguments as MutableList<IrExpression?>
    while (list.size <= index) list.add(null)
    list[index] = value
}

/**
 * `expr.arguments[index]` 相当。 範囲外なら null を返す。
 */
internal fun <S : IrSymbol> IrMemberAccessExpression<S>.getArgumentSafe(index: Int): IrExpression? {
    @Suppress("UNCHECKED_CAST")
    val list = arguments as MutableList<IrExpression?>
    return if (index in list.indices) list[index] else null
}

/**
 * `expr.typeArguments[index] = value` 相当。 list が足りない場合は null 埋めで拡張する。
 */
internal fun <S : IrSymbol> IrMemberAccessExpression<S>.setTypeArgumentSafe(
    index: Int,
    value: IrType?,
) {
    @Suppress("UNCHECKED_CAST")
    val list = typeArguments as MutableList<IrType?>
    while (list.size <= index) list.add(null)
    list[index] = value
}

/**
 * `expr.typeArguments[index]` 相当。 範囲外なら null を返す。
 */
internal fun <S : IrSymbol> IrMemberAccessExpression<S>.getTypeArgumentSafe(index: Int): IrType? {
    @Suppress("UNCHECKED_CAST")
    val list = typeArguments as MutableList<IrType?>
    return if (index in list.indices) list[index] else null
}
