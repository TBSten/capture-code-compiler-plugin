package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors

/**
 * `me.tbsten.capture.code.Source(value: String)` filler の値を IR で構築する [BuildFiller]
 * 実装。
 *
 * task-120-B Phase 4b で concrete 化。 既存 `compat-kXXX/filler/SourceFillerBuilder.kt` を
 * 移植したもので、 `IrConstImpl.string(...)` factory は K2.0 - K2.4-RC 全 baseline で参照可能
 * なため main 直接、 `putValueArgument` / `valueParameters` の K2.4-RC drift だけ
 * [CompatContext] (`putCallValueArgument` / `valueParametersOf`) 経由で吸収する。
 *
 * ## 責務
 *
 * - constructor で `me.tbsten.capture.code.Source` annotation class とその `value` parameter
 *   index を eager resolve して保持
 * - [invoke] で `Source(value = site.source)` の `IrConstructorCall` を組み立てる。 `site.source`
 *   は collector 段 ([me.tbsten.capture.code.feature.capturedSources.ir.normalize] 経由) で
 *   normalize 済の string
 *
 * ## resolve 失敗時の挙動
 *
 * runtime 依存 (`me.tbsten.capture.code` annotation module) が不足している場合は
 * [resolveOrNull] が `null` を返す。 これは
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance]
 * 側で当該 marker 全体を書き換え不能として skip する trigger になる。
 */
internal class FillSource private constructor(
    private val sourceType: IrType,
    private val sourceConstructor: IrConstructorSymbol,
    private val sourceValueIndex: Int,
    private val stringType: IrType,
    private val compat: CompatContext,
) : BuildFiller {

    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        val ctorCall = compat.newIrConstructorCall(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = sourceType,
            constructorSymbol = sourceConstructor,
        )
        val sourceConst = IrConstImpl.string(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = stringType,
            value = site.source,
        )
        compat.putCallValueArgument(ctorCall, sourceValueIndex, sourceConst)
        return ctorCall
    }

    companion object {

        /**
         * `me.tbsten.capture.code.Source` を [pluginContext] から resolve する。 runtime 依存が
         * 不足している場合は `null`。
         */
        fun resolveOrNull(
            pluginContext: IrPluginContext,
            compat: CompatContext,
        ): FillSource? {
            val sourceSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.Source)
                ?: return null
            val sourceConstructor = sourceSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val sourceValueIndex = compat.valueParametersOf(sourceConstructor.owner)
                .indexOfFirst { it.name.asString() == "value" }
                .takeIf { it >= 0 } ?: return null
            return FillSource(
                sourceType = sourceSymbol.typeWith(),
                sourceConstructor = sourceConstructor,
                sourceValueIndex = sourceValueIndex,
                stringType = pluginContext.irBuiltIns.stringType,
                compat = compat,
            )
        }
    }
}
