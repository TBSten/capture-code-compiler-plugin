package me.tbsten.capture.code.compat.k2000.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors

/**
 * `me.tbsten.capture.code.CaptureKind(value: Kind)` filler の値を IR で構築する [FillerBuilder]。
 *
 * task-013 で新規追加。`CapturedSite.kind` を `CaptureKind.Kind` enum 値に map し、
 * `IrGetEnumValueImpl` で参照式を組み立てて `CaptureKind` annotation constructor に put する。
 *
 * ## kind マッピング
 *
 * `CapturedSite.CaptureKind` (compat module) と `me.tbsten.capture.code.CaptureKind.Kind`
 * (runtime annotation の enum) の対応:
 *
 * | CapturedSite.CaptureKind | CaptureKind.Kind |
 * |--------------------------|------------------|
 * | PROPERTY | PROPERTY |
 * | CLASS | CLASS |
 * | OBJECT | OBJECT |
 * | FUNCTION | FUNCTION |
 * | TYPEALIAS | TYPEALIAS |
 * | FILE (task-016) | FILE |
 *
 * 将来 EXPRESSION が追加されたら本 builder の [kindEnumEntries] map に entry を足すだけ。
 * UNKNOWN は collector が site を生成する時点で具体的な kind を埋めているので、本 builder では
 * 使わない (=  fallback の代わりに「未知 kind は build を例外的に処理しない」前提)。
 */
internal class CaptureKindFillerBuilder(
    private val captureKindType: IrType,
    private val captureKindConstructor: IrConstructorSymbol,
    private val captureKindValueIndex: Int,
    private val kindEnumType: IrType,
    private val kindEnumEntries: Map<CapturedSite.CaptureKind, IrEnumEntrySymbol>,
) : FillerBuilder {

    override fun build(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        val entrySymbol = kindEnumEntries[site.kind]
            // 未マッピングの kind (= 将来追加された UNKNOWN/EXPRESSION/FILE 等) は UNKNOWN にフォールバック。
            // 現状 collector は 5 種類しか生成しないので実質到達しない path。
            ?: kindEnumEntries.values.first()

        val kindValue = IrGetEnumValueImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = kindEnumType,
            symbol = entrySymbol,
        )

        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = captureKindType,
            constructorSymbol = captureKindConstructor,
        ).apply {
            putValueArgument(captureKindValueIndex, kindValue)
        }
    }

    companion object {

        /**
         * `me.tbsten.capture.code.CaptureKind` と nested enum `Kind` を [pluginContext] から
         * resolve する。必要な enum entry が 1 つも見つからなければ `null`。
         */
        fun resolve(pluginContext: IrPluginContext): CaptureKindFillerBuilder? {
            val captureKindSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.CaptureKind)
                ?: return null
            val captureKindConstructor = captureKindSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val captureKindValueIndex = captureKindConstructor.owner.valueParameters
                .indexOfFirst { it.name.asString() == "value" }
                .takeIf { it >= 0 } ?: return null

            val kindEnumSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.CaptureKindKind)
                ?: return null
            val kindEnumClass = kindEnumSymbol.owner
            if (kindEnumClass.kind != ClassKind.ENUM_CLASS) return null

            // enum entry を name で検索して CapturedSite.CaptureKind に map する
            val byName = kindEnumClass.declarations
                .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrEnumEntry>()
                .associateBy { it.name.asString() }
            val entries = mutableMapOf<CapturedSite.CaptureKind, IrEnumEntrySymbol>()
            CapturedSite.CaptureKind.values().forEach { siteKind ->
                val entryName = siteKind.name // PROPERTY / CLASS / OBJECT / FUNCTION / TYPEALIAS
                val entry = byName[entryName] ?: return null
                entries[siteKind] = entry.symbol
            }
            return CaptureKindFillerBuilder(
                captureKindType = captureKindSymbol.typeWith(),
                captureKindConstructor = captureKindConstructor,
                captureKindValueIndex = captureKindValueIndex,
                kindEnumType = kindEnumSymbol.typeWith(),
                kindEnumEntries = entries,
            )
        }
    }
}
