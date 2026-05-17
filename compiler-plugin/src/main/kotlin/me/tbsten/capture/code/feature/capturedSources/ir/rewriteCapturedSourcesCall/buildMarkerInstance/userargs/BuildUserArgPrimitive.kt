package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * EXPRESSION 起源 (式 annotation) の場合、 IR phase で marker `IrConstructorCall` が残らないため、
 * FIR session storage から渡された primitive 引数を IR const に再構築する helper class。
 *
 * task-120-B Phase 4a で signature を確定。 invoke 本体は **Phase 4b 持ち越し**。
 *
 * ## 責務 (Phase 4b 以降)
 *
 * FIR から push された [value] を [parameter] の型に合った IR 式に変換する:
 * - `Int` / `Long` / `Short` / `Byte` / `Boolean` / `Char` / `Float` / `Double` → `IrConstImpl.xxx`
 * - `String` + parameter 型 String → `IrConstImpl.string`
 * - `String` + parameter 型 enum class → `IrGetEnumValueImpl` を組み立て
 *
 * 変換不可なら `null` を返す (= 上位 [BuildMarkerInstance] が [BuildUserArg] の default 値経路に
 * fallback する)。
 *
 * ## 旧構造との関係
 *
 * 既存 `K{XXX}/userargs/UserArgPrimitiveIrBuilder.kt` の `buildOrNull` 関数がそのまま残り、
 * runtime path として機能する。 Phase 4b で本 class invoke にロジック移植し、 IR const 構築は
 * 必要に応じて SPI primitive に逃がす。
 */
internal class BuildUserArgPrimitive {

    /**
     * FIR から push された [value] を [parameter] の型に合った IR 式に変換して返す。
     *
     * Phase 4b で concrete impl を入れる。 signature は既存 `UserArgPrimitiveIrBuilder.buildOrNull`
     * と同じ (= 第 3 引数 [pluginContext] は enum class symbol 解決等に使う)。
     */
    @Suppress("UNUSED_PARAMETER")
    internal operator fun invoke(
        value: Any?,
        parameter: IrValueParameter,
        pluginContext: IrPluginContext,
    ): IrExpression? =
        throw UnsupportedOperationException(
            "BuildUserArgPrimitive.invoke is a Phase 4a signature placeholder. " +
                "Concrete impl arrives in task-120-B Phase 4b.",
        )
}
