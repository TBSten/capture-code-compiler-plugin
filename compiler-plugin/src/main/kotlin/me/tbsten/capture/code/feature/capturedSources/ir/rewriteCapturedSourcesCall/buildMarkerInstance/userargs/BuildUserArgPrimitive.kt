package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.UserArgValue
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType

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
 * - primitive: `Int`, `Long`, `Short`, `Byte`, `Boolean`, `Char`, `Float`, `Double`, `String`
 *   ([UserArgValue.IntValue] / [UserArgValue.StringValue] 等の各 branch)
 * - enum: [UserArgValue.EnumRef] (FqN を受け取り IR の `IrGetEnumValue` を組み立てる)
 *
 * ## 非対応 (将来拡張予定)
 *
 * - 配列 (`vararg`)
 * - nested annotation
 * - KClass の正確な IR 化 ([UserArgValue.ClassRef] を受け取るが現状 `null` を返す)
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
     *
     * task-133 (2026-05-21): [value] の型を `Any?` から [UserArgValue]`?` に変更。 sealed 化
     * された各 branch に対応する exhaustive `when` で IR 式を組み立てる。 旧 `Any?` 経路の
     * `is Int -> ...` 暗黙の sum type 分岐を型に持ち上げ、 enum FqN と class FqN の混在
     * (= 旧 `String -> buildStringOrEnum`) を [UserArgValue.EnumRef] / [UserArgValue.ClassRef]
     * / [UserArgValue.StringValue] で 3 分離した。
     */
    internal operator fun invoke(
        value: UserArgValue?,
        parameter: IrValueParameter,
        pluginContext: IrPluginContext,
        compat: CompatContext,
    ): IrExpression? {
        if (value == null) return null
        val type = parameter.type
        return when (value) {
            UserArgValue.NullValue -> null
            is UserArgValue.BoolValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Boolean, value.value)
            is UserArgValue.CharValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Char, value.value)
            is UserArgValue.ByteValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Byte, value.value)
            is UserArgValue.ShortValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Short, value.value)
            is UserArgValue.IntValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Int, value.value)
            is UserArgValue.LongValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Long, value.value)
            is UserArgValue.FloatValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Float, value.value)
            is UserArgValue.DoubleValue ->
                compat.newIrConstPrimitive(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, IrConstKind.Double, value.value)
            is UserArgValue.StringValue ->
                compat.newIrConstString(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, value.value)
            is UserArgValue.EnumRef -> buildEnum(value.entryFqn, parameter, pluginContext, compat)
            // class FqN を IR `IrGetClass` に再構築する経路は未対応 (0.4.0+ で着手余地)。
            // 旧 `Any?` 経路でも null 返却していたため挙動互換。
            is UserArgValue.ClassRef -> null
        }
    }

    /**
     * parameter 型が enum class の場合に、 末尾の `.Xxx` を entry 名として
     * SPI 経由で `IrGetEnumValue` を組み立てる。 task-133 で `buildStringOrEnum` から
     * pure enum 経路として切り出し (旧 String 経路は `StringValue` branch で吸収済)。
     *
     * [entryFqn] は `com.example.Verb.GET` のような FqN 想定。 末尾セグメントを entry 名とする。
     */
    private fun buildEnum(
        entryFqn: String,
        parameter: IrValueParameter,
        @Suppress("UNUSED_PARAMETER") pluginContext: IrPluginContext,
        compat: CompatContext,
    ): IrExpression? {
        val classifier = (parameter.type as? IrSimpleType)?.classifier
        val ownerClass = classifier?.owner as? IrClass ?: return null
        val entryName = entryFqn.substringAfterLast('.')
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
