package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.compat.CapturedSite
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Logic H (`capturedSources<T>()` 書き換え) の Kotlin 2.0.0 向け実装。
 *
 * 入力された [IrCall] (= `me.tbsten.capture.code.capturedSources<Marker>()`、`Marker` は
 * [me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry] に登録された marker のいずれか) を、
 * [K2000CapturedSourcesCollector] が収集した [CapturedSite] のリストから組み立てた
 * `listOf(Marker(source = Source(value = "...")))` 相当の [IrCall] (`kotlin.collections.listOf`) に
 * 書き換える。
 *
 * task-008 (Logic A) で hardcoded marker FqN list は撤廃された。marker 集合は FIR phase が
 * `@CaptureCode` メタアノテーションから動的に検出するようになっている。
 *
 * Phase 1 制約 (未対応):
 * - filler は `Source(value: String)` のみ。`SourceLocation` / `CaptureKind` / ユーザ定義パラメータは未対応
 *   (Phase 2 task 2.4 / 2.5 で実装)
 * - marker の primary constructor は `source: Source = Source()` を持つ前提
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic H / §7.9 Marker annotation instance の構築 を参照。
 */
internal object K2000CapturedSourcesRewriter {

    /** 書き換え対象となる `capturedSources<T>()` の完全修飾名。 */
    const val CAPTURED_SOURCES_FQN: String = "me.tbsten.capture.code.capturedSources"

    /** filler 型 `Source(value: String)` の FqN。 */
    private const val SOURCE_FQN: String = "me.tbsten.capture.code.Source"

    /**
     * 与えられた [original] を [sites] から組み立てた `listOf<Marker>(...)` の [IrCall] に書き換える。
     *
     * [markerFqn] は `capturedSources<T>()` の type argument T (= 書き換え結果の list 要素型)。
     * `sites` は当該 marker でフィルタ済みの [CapturedSite] であることを呼び出し側が保証する。
     *
     * marker class / Source class が解決できない場合 (= runtime 依存が不足) は `null` を返し、
     * 呼び出し側は元の [IrCall] をそのまま返すこと。
     */
    fun rewriteCapturedSourcesCall(
        original: IrCall,
        markerFqn: String,
        sites: List<CapturedSite>,
        pluginContext: IrPluginContext,
    ): IrCall? {
        val snippetsClassId = ClassId.topLevel(FqName(markerFqn))
        val sourceClassId = ClassId.topLevel(FqName(SOURCE_FQN))

        val snippetsSymbol = pluginContext.referenceClass(snippetsClassId) ?: return null
        val sourceSymbol = pluginContext.referenceClass(sourceClassId) ?: return null

        val snippetsConstructor = snippetsSymbol.primaryConstructorOrNull() ?: return null
        val sourceConstructor = sourceSymbol.primaryConstructorOrNull() ?: return null
        val sourceValueIndex = sourceConstructor.indexOfValueParameterByName("value") ?: return null
        val snippetsSourceIndex = snippetsConstructor.indexOfValueParameterByName("source") ?: return null

        // Phase 1 制約: filler `Source` 以外で必須 (no-default) のパラメータが marker constructor に
        // 残っている場合、ユーザ定義パラメータの値を IR 化する logic がまだ無い (task-014 の責務)
        // ため書き換えできない。書き換えをスキップし元の IrCall を返させる。
        // 本制約は Phase 2 task 2.4 / 2.5 (filler 自動値埋め + ユーザ定義パラメータ保持) で解消する。
        if (snippetsConstructor.hasNonFillerRequiredParameters(snippetsSourceIndex)) return null

        val listOfSymbol = pluginContext.findListOfVararg() ?: return null

        val snippetsType = snippetsSymbol.defaultTypeProjection()
        val listElements = sites.map { site ->
            buildSnippetsCall(
                site = site,
                snippetsType = snippetsType,
                snippetsConstructor = snippetsConstructor,
                snippetsSourceIndex = snippetsSourceIndex,
                sourceType = sourceSymbol.defaultTypeProjection(),
                sourceConstructor = sourceConstructor,
                sourceValueIndex = sourceValueIndex,
                stringType = pluginContext.irBuiltIns.stringType,
            )
        }

        val listType = pluginContext.irBuiltIns.listClass.typeWith(snippetsType)
        val varargType = pluginContext.irBuiltIns.arrayClass.typeWith(snippetsType)

        return IrCallImpl(
            startOffset = original.startOffset,
            endOffset = original.endOffset,
            type = listType,
            symbol = listOfSymbol,
            typeArgumentsCount = 1,
            valueArgumentsCount = 1,
        ).apply {
            putTypeArgument(0, snippetsType)
            putValueArgument(
                0,
                IrVarargImpl(
                    startOffset = original.startOffset,
                    endOffset = original.endOffset,
                    type = varargType,
                    varargElementType = snippetsType,
                    elements = listElements.toList<IrVarargElement>(),
                ),
            )
        }
    }

    private fun buildSnippetsCall(
        site: CapturedSite,
        snippetsType: IrType,
        snippetsConstructor: IrConstructorSymbol,
        snippetsSourceIndex: Int,
        sourceType: IrType,
        sourceConstructor: IrConstructorSymbol,
        sourceValueIndex: Int,
        stringType: IrType,
    ): IrExpression {
        val sourceCall = IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = sourceType,
            constructorSymbol = sourceConstructor,
        ).apply {
            putValueArgument(
                sourceValueIndex,
                IrConstImpl.string(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = stringType,
                    value = site.source,
                ),
            )
        }

        return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = snippetsType,
            constructorSymbol = snippetsConstructor,
        ).apply {
            putValueArgument(snippetsSourceIndex, sourceCall)
        }
    }

    private fun IrClassSymbol.primaryConstructorOrNull(): IrConstructorSymbol? =
        owner.constructors.firstOrNull { it.isPrimary }?.symbol
            ?: owner.constructors.firstOrNull()?.symbol

    private fun IrConstructorSymbol.indexOfValueParameterByName(name: String): Int? {
        val parameters = owner.valueParameters
        val index = parameters.indexOfFirst { it.name.asString() == name }
        return index.takeIf { it >= 0 }
    }

    /**
     * marker の primary constructor が、`source: Source` 以外で **必須 (no-default)** のパラメータを
     * 1 つでも持っていれば `true`。
     *
     * Phase 1 では `source` filler のみ自動で埋められるため、それ以外の必須パラメータがある場合は
     * `IrConstructorCall` 構築時に値を渡せず compile error になる。書き換えを skip させるために使う。
     *
     * デフォルト値あり (`hasDefaultValue = true`) は無視 — compiler 自体が default を埋めるので
     * 書き換え対象外でも constructor を組み立てられる。
     *
     * @param sourceParameterIndex `source: Source` パラメータの index
     */
    private fun IrConstructorSymbol.hasNonFillerRequiredParameters(sourceParameterIndex: Int): Boolean {
        return owner.valueParameters.withIndex().any { (index, parameter) ->
            index != sourceParameterIndex && !parameter.hasDefaultValue()
        }
    }

    private fun org.jetbrains.kotlin.ir.declarations.IrValueParameter.hasDefaultValue(): Boolean =
        defaultValue != null

    private fun IrClassSymbol.defaultTypeProjection(): IrType = typeWith()

    /**
     * `kotlin.collections.listOf(vararg elements: T): List<T>` の symbol を解決する。
     *
     * `listOf` には複数オーバーロード (`()`, `(T)`, `(vararg T)`) があるため、type parameter が 1 つで
     * かつ value parameter が 1 つ (= vararg) のものを採用する。
     */
    private fun IrPluginContext.findListOfVararg(): IrSimpleFunctionSymbol? {
        val callableId = CallableId(
            packageName = FqName("kotlin.collections"),
            callableName = Name.identifier("listOf"),
        )
        return referenceFunctions(callableId).firstOrNull { symbol ->
            val function = symbol.owner
            function.typeParameters.size == 1 &&
                function.valueParameters.size == 1 &&
                function.valueParameters[0].varargElementType != null
        }
    }
}
