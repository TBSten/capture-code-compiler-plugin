// task-076: Kotlin 2.4.0-RC drift — putValueArgument / valueParameters は削除された。
// 内部 shim [putArgumentSafe] + `nonDispatchParameters` を使用する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.compat.k240rc.putArgumentSafe
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

/**
 * `me.tbsten.capture.code.Source(value: String)` filler の値を IR で構築する [FillerBuilder]。
 *
 * `K240RcCapturedSourcesRewriter` から builder クラスとして切り出している。 declaration 起源
 * (Phase 1) / file annotation 起源など複数の collect 経路で同じ builder を再利用する。
 *
 * 値: `Source(value = site.source)` — [CapturedSite.source] は collector 段で
 * [me.tbsten.capture.code.feature.captured_sources.normalize.normalize] による正規化を
 * 終えた状態で来る。
 */
internal class SourceFillerBuilder(
    private val sourceType: IrType,
    private val sourceConstructor: IrConstructorSymbol,
    private val sourceValueIndex: Int,
    private val stringType: IrType,
) : FillerBuilder {

    override fun build(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = sourceType,
            constructorSymbol = sourceConstructor,
        ).apply {
            putArgumentSafe(
                sourceValueIndex,
                IrConstImpl.string(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = stringType,
                    value = site.source,
                ),
            )
        }
    }

    companion object {

        /**
         * `me.tbsten.capture.code.Source` を [pluginContext] から resolve して
         * [SourceFillerBuilder] を生成する。runtime 依存が不足している場合は `null`。
         */
        fun resolve(pluginContext: IrPluginContext): SourceFillerBuilder? {
            val sourceSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.Source)
                ?: return null
            val sourceConstructor = sourceSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val sourceValueIndex = sourceConstructor.owner.nonDispatchParameters
                .indexOfFirst { it.name.asString() == "value" }
                .takeIf { it >= 0 } ?: return null
            return SourceFillerBuilder(
                sourceType = sourceSymbol.typeWith(),
                sourceConstructor = sourceConstructor,
                sourceValueIndex = sourceValueIndex,
                stringType = pluginContext.irBuiltIns.stringType,
            )
        }
    }
}
