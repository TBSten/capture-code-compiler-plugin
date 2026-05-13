package me.tbsten.capture.code.compat.k2000

import com.google.auto.service.AutoService
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.compat.IrInjector
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Kotlin 2.0.0+ 向けの IR 変換実装。
 *
 * Phase 1 (task-005 / task-006) では hardcoded marker (`com.example.Snippets`) 付きの
 * property を走査してソース文字列を収集し、後段で `capturedSources<Snippets>()` 呼び出しを
 * `listOf(Snippets(source = Source(...)))` へ書き換える。
 *
 * 詳細な順序は `compiler-plugin-design.md` §6 Phase ordering 参照。
 */
public class K2000IrInjector : IrInjector {
    override fun transform(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val transformer = K2000CapturedSourcesTransformer(pluginContext)
        moduleFragment.transformChildrenVoid(transformer)
    }

    @AutoService(IrInjector.Factory::class)
    public class Factory : IrInjector.Factory {
        override val minVersion: String = "2.0.0"
        override fun create(): IrInjector = K2000IrInjector()
    }
}

/**
 * IR を走査して `@CaptureCode` 由来のキャプチャを構築する transformer。
 *
 * Phase 1 vertical slice の責務分担:
 * - task-005 … [K2000CapturedSourcesCollector] を `IrFile` ごとに走らせて
 *   [capturedSites] に [CapturedSite] を蓄積する (Logic B / C 最小実装)
 * - task-006 (本 ticket 範囲) … [visitCall] で `capturedSources<Snippets>()` を検出し、
 *   [capturedSites] から組み立てた `listOf(Snippets(...))` の [IrCall] に置換する (Logic H)
 *
 * `capturedSites` はモジュール 1 回の transform 内で蓄積される。collect → rewrite の順序は
 * [visitFile] で先に collector を走らせることで担保している ([K2000CapturedSourcesCollector]
 * の完了メモ #2 参照)。Phase 2 で marker 多型化に伴い `Map<MarkerFqn, List<CapturedSite>>`
 * への昇格を予定。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic B/C/D/H、§6 Phase ordering 参照。
 */
internal class K2000CapturedSourcesTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    /**
     * task-005 で収集した capture サイトの一覧。task-006 の書き換え step が参照する。
     *
     * Phase 1 では marker FqN が `com.example.Snippets` 1 種類のみで、すべて property。
     * Phase 2 で marker 多型化と種別拡張に伴い構造を見直す。
     */
    val capturedSites: MutableList<CapturedSite> = mutableListOf()

    override fun visitFile(declaration: IrFile): IrFile {
        val collector = K2000CapturedSourcesCollector(declaration)
        declaration.acceptChildrenVoid(collector)
        capturedSites += collector.capturedSites
        return super.visitFile(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        // 子要素を先に処理 (nested capturedSources<T>() 呼び出しに備える)
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed

        if (!transformed.isCapturedSourcesCall()) return transformed
        // TODO: Phase 2 で Logic A 動的検出に置換する。それまでは hardcoded marker 限定。
        if (!transformed.hasHardcodedMarkerTypeArgument()) return transformed

        val rewritten = K2000CapturedSourcesRewriter.rewriteCapturedSourcesCall(
            original = transformed,
            sites = capturedSites.filter { it.markerFqn == K2000CapturedSourcesRewriter.HARDCODED_MARKER_FQN },
            pluginContext = pluginContext,
        )
        return rewritten ?: transformed
    }

    private fun IrCall.isCapturedSourcesCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == K2000CapturedSourcesRewriter.CAPTURED_SOURCES_FQN

    private fun IrCall.hasHardcodedMarkerTypeArgument(): Boolean {
        val typeArg = getTypeArgument(0) ?: return false
        return typeArg.classFqName?.asString() == K2000CapturedSourcesRewriter.HARDCODED_MARKER_FQN
    }
}
