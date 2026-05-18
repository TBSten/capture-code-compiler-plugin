package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors

/**
 * `me.tbsten.capture.code.CaptureKind(value: Kind)` filler の値を IR で構築する [BuildFiller]
 * 実装。
 *
 * task-120-B Phase 4b で concrete 化。 既存 `compat-kXXX/filler/CaptureKindFillerBuilder.kt` を
 * 移植したもので、 IR 構築 API (`IrGetEnumValueImpl` / `IrConstructorCallImpl.fromSymbolOwner`) は
 * K2.0 baseline で参照可能なため main 直接、 `putValueArgument` / `valueParameters` の K2.4-RC
 * drift だけ [CompatContext] (`putCallValueArgument` / `valueParametersOf`) 経由で吸収する。
 *
 * ## 責務
 *
 * - constructor で `me.tbsten.capture.code.CaptureKind` annotation class と nested `Kind` enum を
 *   eager resolve し、 enum entry を [CapturedSite.CaptureKind] の name でひもづけて保持
 * - [invoke] で `site.kind` に対応する enum entry を `IrGetEnumValueImpl` に包み、
 *   `CaptureKind(value = ...)` の `IrConstructorCall` を組み立てる
 *
 * ## resolve 失敗時の挙動
 *
 * runtime 依存 (`me.tbsten.capture.code` annotation module) が不足している場合は
 * [resolveOrNull] が `null` を返す。 これは
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance]
 * 側で当該 marker 全体を書き換え不能として skip する trigger になる。
 */
internal class FillCaptureKind private constructor(
    private val captureKindType: IrType,
    private val captureKindConstructor: IrConstructorSymbol,
    private val captureKindValueIndex: Int,
    private val kindEnumType: IrType,
    private val kindEnumEntries: Map<CapturedSite.CaptureKind, IrEnumEntrySymbol>,
    private val compat: CompatContext,
) : BuildFiller {

    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        val entrySymbol = kindEnumEntries[site.kind]
            // 未マッピングの kind (将来 enum 拡張があった場合のフォールバック)。 現状は site.kind
            // がすべて [resolveOrNull] で集めた entries に含まれているので実質到達しない。
            ?: kindEnumEntries.values.first()

        val kindValue = compat.newIrGetEnumValue(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = kindEnumType,
            symbol = entrySymbol,
        )

        val ctorCall = compat.newIrConstructorCall(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = captureKindType,
            constructorSymbol = captureKindConstructor,
        )
        compat.putCallValueArgument(ctorCall, captureKindValueIndex, kindValue)
        return ctorCall
    }

    companion object {

        /**
         * `me.tbsten.capture.code.CaptureKind` と nested enum `Kind` を [pluginContext] から
         * resolve する。 必要な class / enum entry のいずれかが見つからなければ `null`。
         */
        fun resolveOrNull(
            pluginContext: IrPluginContext,
            compat: CompatContext,
        ): FillCaptureKind? {
            val captureKindSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.CaptureKind)
                ?: return null
            val captureKindConstructor = captureKindSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val captureKindValueIndex = compat.valueParametersOf(captureKindConstructor.owner)
                .indexOfFirst { it.name.asString() == "value" }
                .takeIf { it >= 0 } ?: return null

            val kindEnumSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.CaptureKindKind)
                ?: return null
            val kindEnumClass = kindEnumSymbol.owner
            if (kindEnumClass.kind != ClassKind.ENUM_CLASS) return null

            val byName = kindEnumClass.declarations
                .filterIsInstance<IrEnumEntry>()
                .associateBy { it.name.asString() }
            val entries = mutableMapOf<CapturedSite.CaptureKind, IrEnumEntrySymbol>()
            CapturedSite.CaptureKind.values().forEach { siteKind ->
                val entry = byName[siteKind.name] ?: return null
                entries[siteKind] = entry.symbol
            }
            return FillCaptureKind(
                captureKindType = captureKindSymbol.typeWith(),
                captureKindConstructor = captureKindConstructor,
                captureKindValueIndex = captureKindValueIndex,
                kindEnumType = kindEnumSymbol.typeWith(),
                kindEnumEntries = entries,
                compat = compat,
            )
        }
    }
}
