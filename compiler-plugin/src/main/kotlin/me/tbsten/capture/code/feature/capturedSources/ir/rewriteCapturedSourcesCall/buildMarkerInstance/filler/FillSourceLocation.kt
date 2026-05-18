package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors

/**
 * `me.tbsten.capture.code.SourceLocation(packageName, filePath, startLine, endLine)` filler の
 * 値を IR で構築する [BuildFiller] 実装。
 *
 * task-120-B Phase 4b で concrete 化。 既存 `compat-kXXX/filler/SourceLocationFillerBuilder.kt`
 * を移植したもので、 `IrConstImpl.string(...)` / `IrConstImpl.int(...)` factory は K2.0 -
 * K2.4-RC 全 baseline で参照可能なため main 直接、 `putValueArgument` / `valueParameters` の
 * K2.4-RC drift だけ [CompatContext] (`putCallValueArgument` / `valueParametersOf`) 経由で吸収する。
 *
 * ## 責務
 *
 * - constructor で `me.tbsten.capture.code.SourceLocation` annotation class と 4 つの parameter
 *   index (packageName / filePath / startLine / endLine) を eager resolve して保持
 * - [invoke] で `SourceLocation(packageName, filePath, startLine, endLine)` の
 *   `IrConstructorCall` を組み立てる
 *
 * ## `includeLineInfo` 対応
 *
 * `config.includeLineInfo = false` の場合は `startLine` / `endLine` を 0 で埋める
 * (= filler のデフォルト値と同じ)。 design §11 open question #1 の挙動。
 */
internal class FillSourceLocation private constructor(
    private val locationType: IrType,
    private val locationConstructor: IrConstructorSymbol,
    private val packageNameIndex: Int,
    private val filePathIndex: Int,
    private val startLineIndex: Int,
    private val endLineIndex: Int,
    private val stringType: IrType,
    private val intType: IrType,
    private val compat: CompatContext,
) : BuildFiller {

    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        val effectiveStartLine = if (config.includeLineInfo) site.startLine else 0
        val effectiveEndLine = if (config.includeLineInfo) site.endLine else 0
        val ctorCall = compat.newIrConstructorCall(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = locationType,
            constructorSymbol = locationConstructor,
        )
        compat.putCallValueArgument(ctorCall, packageNameIndex, stringConst(site.packageFqn))
        compat.putCallValueArgument(ctorCall, filePathIndex, stringConst(site.filePath))
        compat.putCallValueArgument(ctorCall, startLineIndex, intConst(effectiveStartLine))
        compat.putCallValueArgument(ctorCall, endLineIndex, intConst(effectiveEndLine))
        return ctorCall
    }

    private fun stringConst(value: String): IrExpression = compat.newIrConstString(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = stringType,
        value = value,
    )

    private fun intConst(value: Int): IrExpression = compat.newIrConstInt(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = intType,
        value = value,
    )

    companion object {

        /**
         * `me.tbsten.capture.code.SourceLocation` を [pluginContext] から resolve する。 必要な
         * 4 parameter (packageName / filePath / startLine / endLine) すべての index が解決
         * できなければ `null`。
         */
        fun resolveOrNull(
            pluginContext: IrPluginContext,
            compat: CompatContext,
        ): FillSourceLocation? {
            val locationSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.SourceLocation)
                ?: return null
            val locationConstructor = locationSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val params: List<IrValueParameter> = compat.valueParametersOf(locationConstructor.owner)

            fun indexOf(name: String): Int? =
                params.indexOfFirst { it.name.asString() == name }.takeIf { it >= 0 }

            val packageNameIndex = indexOf("packageName") ?: return null
            val filePathIndex = indexOf("filePath") ?: return null
            val startLineIndex = indexOf("startLine") ?: return null
            val endLineIndex = indexOf("endLine") ?: return null
            return FillSourceLocation(
                locationType = locationSymbol.typeWith(),
                locationConstructor = locationConstructor,
                packageNameIndex = packageNameIndex,
                filePathIndex = filePathIndex,
                startLineIndex = startLineIndex,
                endLineIndex = endLineIndex,
                stringType = pluginContext.irBuiltIns.stringType,
                intType = pluginContext.irBuiltIns.intType,
                compat = compat,
            )
        }
    }
}
