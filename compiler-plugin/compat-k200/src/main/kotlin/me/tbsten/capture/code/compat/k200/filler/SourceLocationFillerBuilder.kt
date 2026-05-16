package me.tbsten.capture.code.compat.k200.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors

/**
 * `me.tbsten.capture.code.SourceLocation(packageName, filePath, startLine, endLine)` filler の
 * 値を IR で構築する [FillerBuilder]。
 *
 * 各 [CapturedSite] が保持する `packageFqn` / `filePath` / `startLine` / `endLine`
 * (collector 段で `IrFile.fqName` / `IrFileEntry.name` / `IrFileEntry.getLineNumber+1` で
 * 計算済) を、 `SourceLocation` annotation の 4 パラメータに put する。
 *
 * ## `includeLineInfo` 対応
 *
 * `config.includeLineInfo = false` の場合は `startLine` / `endLine` を 0 で埋める
 * (= filler のデフォルト値と同じ)。 design §11 open question #1 の挙動。
 *
 * ## `filePath` の厳密化 (TODO)
 *
 * 現状 `filePath` は `IrFile.fileEntry.name` (絶対パス) をそのまま使う。 本来は Gradle module root
 * からの相対 path が望ましいが、 IrFile から module root を解決する手段が安定 API として無いため
 * Phase 2 ではこのまま採用する。
 */
internal class SourceLocationFillerBuilder(
    private val locationType: IrType,
    private val locationConstructor: IrConstructorSymbol,
    private val packageNameIndex: Int,
    private val filePathIndex: Int,
    private val startLineIndex: Int,
    private val endLineIndex: Int,
    private val stringType: IrType,
    private val intType: IrType,
) : FillerBuilder {

    override fun build(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression {
        val effectiveStartLine = if (config.includeLineInfo) site.startLine else 0
        val effectiveEndLine = if (config.includeLineInfo) site.endLine else 0
        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = locationType,
            constructorSymbol = locationConstructor,
        ).apply {
            putValueArgument(packageNameIndex, stringConst(site.packageFqn))
            putValueArgument(filePathIndex, stringConst(site.filePath))
            putValueArgument(startLineIndex, intConst(effectiveStartLine))
            putValueArgument(endLineIndex, intConst(effectiveEndLine))
        }
    }

    private fun stringConst(value: String): IrExpression = IrConstImpl.string(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = stringType,
        value = value,
    )

    private fun intConst(value: Int): IrExpression = IrConstImpl.int(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = intType,
        value = value,
    )

    companion object {

        /**
         * `me.tbsten.capture.code.SourceLocation` を [pluginContext] から resolve する。
         * 必要な 4 パラメータ (packageName / filePath / startLine / endLine) すべての index が
         * 解決できなければ `null`。
         */
        fun resolve(pluginContext: IrPluginContext): SourceLocationFillerBuilder? {
            val locationSymbol = pluginContext.referenceClass(CaptureCodeFillerClassIds.SourceLocation)
                ?: return null
            val locationConstructor = locationSymbol.owner.constructors.firstOrNull()?.symbol
                ?: return null
            val params: List<IrValueParameter> = locationConstructor.owner.valueParameters

            fun indexOf(name: String): Int? =
                params.indexOfFirst { it.name.asString() == name }.takeIf { it >= 0 }

            val packageNameIndex = indexOf("packageName") ?: return null
            val filePathIndex = indexOf("filePath") ?: return null
            val startLineIndex = indexOf("startLine") ?: return null
            val endLineIndex = indexOf("endLine") ?: return null
            return SourceLocationFillerBuilder(
                locationType = locationSymbol.typeWith(),
                locationConstructor = locationConstructor,
                packageNameIndex = packageNameIndex,
                filePathIndex = filePathIndex,
                startLineIndex = startLineIndex,
                endLineIndex = endLineIndex,
                stringType = pluginContext.irBuiltIns.stringType,
                intType = pluginContext.irBuiltIns.intType,
            )
        }
    }
}
