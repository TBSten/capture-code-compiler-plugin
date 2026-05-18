package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * EXPRESSION 起源 (式 annotation) の場合、 IR phase で marker `IrConstructorCall` が残らない
 * ため、 FIR session storage から渡された primitive 引数を IR const に再構築する helper class。
 * task-120-B Phase 4b で concrete 化。
 *
 * 既存 `compat-kXXX/userargs/UserArgPrimitiveIrBuilder.kt` (`buildOrNull`) を移植したもの。 利用
 * している IR API (`IrConstImpl` 5-arg ctor / `IrConstKind` / `IrGetEnumValueImpl` 4-arg ctor) は
 * いずれも K2.0 - K2.4-RC 全 baseline で参照可能なため、 main bytecode から直接呼べる
 * (CompatContext additive 追加不要)。
 *
 * ## サポートする primitive 種別
 *
 * - `Int`, `Long`, `Short`, `Byte`, `Boolean`, `Char`, `Float`, `Double`, `String`
 * - enum (`String` で FqN を受け取り IR の `IrGetEnumValue` を組み立てる)
 *
 * ## 非対応 (将来拡張予定)
 *
 * - 配列 (`vararg`)
 * - nested annotation
 * - KClass の正確な IR 化 (本 builder では String FqN を受け取って IR を組み立てない)
 *
 * ## 旧構造との関係
 *
 * 既存 `K{XXX}/userargs/UserArgPrimitiveIrBuilder.kt` は runtime path として並行存続する。
 * Phase 5 で `transformIr` を main 経由に切り替えた時点で本 class が runtime path になり、
 * Phase 6 で旧 builder 削除予定。
 */
internal class BuildUserArgPrimitive {

    /**
     * FIR から push された [value] を [parameter] の型に合った IR 式に変換して返す。 変換不可なら
     * `null` (= 上位 [BuildMarkerInstance] が [BuildUserArg] の default 値経路に fallback する)。
     *
     * task-0.2.0-cifix-ir (2026-05-19): IR `IrConst*` / `IrGetEnumValue` の構築は
     * [CompatContext] SPI 経由で行う。 main module は K2.0 baseline でコンパイルされる一方、
     * `IrConstImpl` / `IrGetEnumValueImpl` の top-level builder host class は K2.1+ で
     * consolidate されており、 main bytecode が `IrConstImplKt` / `IrGetEnumValueImplKt`
     * を直接参照すると `ClassNotFoundException` を起こすため。
     */
    internal operator fun invoke(
        value: Any?,
        parameter: IrValueParameter,
        pluginContext: IrPluginContext,
        compat: CompatContext,
    ): IrExpression? {
        if (value == null) return null
        val type = parameter.type
        return when (value) {
            is Int -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Int, value)
            is Long -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Long, value)
            is Short -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Short, value)
            is Byte -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Byte, value)
            is Boolean -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Boolean, value)
            is Char -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Char, value)
            is Float -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Float, value)
            is Double -> compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Double, value)
            is String -> {
                // String 引数 / enum entry FqN いずれの可能性もある。 type を見て分岐する。
                // (parameter.type が enum class なら value は FqN 文字列、 String なら value は文字列)
                buildStringOrEnum(value, parameter, pluginContext, compat)
            }
            else -> null
        }
    }

    /**
     * value が String 型 + parameter 型 String → SPI 経由で `IrConst(String)` を直接構築。
     * value が String 型 + parameter 型 enum class → 末尾の `.Xxx` を entry 名として
     * SPI 経由で `IrGetEnumValue` を組み立てる。
     */
    private fun buildStringOrEnum(
        value: String,
        parameter: IrValueParameter,
        @Suppress("UNUSED_PARAMETER") pluginContext: IrPluginContext,
        compat: CompatContext,
    ): IrExpression? {
        val classifier = (parameter.type as? IrSimpleType)?.classifier
        val ownerClass = classifier?.owner as? IrClass
        val paramClassFqn = ownerClass?.fqNameWhenAvailable?.asString()
        if (paramClassFqn == "kotlin.String") {
            return compat.newIrConstString(UNDEFINED_OFFSET, UNDEFINED_OFFSET, parameter.type, value)
        }
        // enum 経路: parameter.type の owner class declarations から該当 entry を探す
        if (ownerClass == null) return null
        // value は `com.example.Verb.GET` のような FqN 想定。 末尾セグメントを entry 名とする。
        val entryName = value.substringAfterLast('.')
        val entry = ownerClass.declarations
            .filterIsInstance<IrEnumEntry>()
            .firstOrNull { it.name.asString() == entryName } ?: return null
        return compat.newIrGetEnumValue(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = parameter.type,
            symbol = entry.symbol,
        )
    }
}
