package me.tbsten.capture.code.compat.k200.userargs

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * EXPRESSION 起源 (式 annotation) の場合、 IR phase で marker `IrConstructorCall` が残らない
 * (spike R1 で確認済) ため、 ユーザが式 annotation に書いた primitive 引数を
 * **FIR session storage 経由で受け取った値** (= `Any?`) から IR const を再構築する。
 *
 * サポートする primitive 種別:
 * - `Int`, `Long`, `Short`, `Byte`, `Boolean`, `Char`, `Float`, `Double`, `String`
 * - enum (`String` で FqN を受け取り IR の `IrGetEnumValue` を組み立て)
 *
 * 現状の主用途は「filler のみが入った marker (ケース #7, #67)」のため、
 * 多くの test ケースでは本 builder が呼ばれない (= [buildOrNull] が `null` を返す)。
 * 上位の rewriter は本 builder が `null` を返した場合に [UserArgIrBuilder.buildOrDefault] で
 * default 値経路にフォールバックする。
 *
 * 非対応 (将来拡張予定):
 * - 配列 (`vararg`)
 * - nested annotation
 * - KClass の正確な IR 化 (本 builder では String FqN を受け取って IR を組み立てない)
 */
internal object UserArgPrimitiveIrBuilder {

    /**
     * FIR から push された [value] を [parameter] の型に合った IR 式に変換して返す。
     *
     * @return IR 式 / 変換不可なら `null` (= 上位は default 値経路へ fallback)
     */
    fun buildOrNull(
        value: Any?,
        parameter: IrValueParameter,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        if (value == null) return null
        val type = parameter.type
        return when (value) {
            is Int -> irConst(type, IrConstKind.Int, value)
            is Long -> irConst(type, IrConstKind.Long, value)
            is Short -> irConst(type, IrConstKind.Short, value)
            is Byte -> irConst(type, IrConstKind.Byte, value)
            is Boolean -> irConst(type, IrConstKind.Boolean, value)
            is Char -> irConst(type, IrConstKind.Char, value)
            is Float -> irConst(type, IrConstKind.Float, value)
            is Double -> irConst(type, IrConstKind.Double, value)
            is String -> {
                // String 引数 / enum entry FqN いずれの可能性もある。 type を見て分岐する。
                // (parameter.type が enum class なら value は FqN 文字列、 String なら value は文字列)
                buildStringOrEnum(value, parameter, pluginContext)
            }
            else -> null
        }
    }

    private fun <T> irConst(type: IrType, kind: IrConstKind<T>, value: T): IrConst<T> =
        IrConstImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = type,
            kind = kind,
            value = value,
        )

    /**
     * value が String 型 + parameter 型 String → IrConst(String) を直接構築。
     * value が String 型 + parameter 型 enum class → 末尾の `.Xxx` を entry 名として
     * `IrGetEnumValueImpl` を組み立てる。
     */
    private fun buildStringOrEnum(
        value: String,
        parameter: IrValueParameter,
        @Suppress("UNUSED_PARAMETER") pluginContext: IrPluginContext,
    ): IrExpression? {
        val classifier = (parameter.type as? IrSimpleType)?.classifier
        val ownerClass = classifier?.owner as? IrClass
        val paramClassFqn = ownerClass?.fqNameWhenAvailable?.asString()
        if (paramClassFqn == "kotlin.String") {
            return irConst(parameter.type, IrConstKind.String, value)
        }
        // enum 経路: parameter.type の owner class declarations から該当 entry を探す
        if (ownerClass == null) return null
        // value は `com.example.Verb.GET` のような FqN 想定。末尾セグメントを entry 名とする。
        val entryName = value.substringAfterLast('.')
        val entry = ownerClass.declarations
            .filterIsInstance<IrEnumEntry>()
            .firstOrNull { it.name.asString() == entryName } ?: return null
        return IrGetEnumValueImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = parameter.type,
            symbol = entry.symbol,
        )
    }
}
