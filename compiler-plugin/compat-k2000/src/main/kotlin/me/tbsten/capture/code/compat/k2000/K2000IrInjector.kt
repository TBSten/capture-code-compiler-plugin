package me.tbsten.capture.code.compat.k2000

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
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
 * task-008 (Logic A 動的検出) 後の責務:
 * - [CaptureCodeMarkerRegistry] (FIR phase で `@CaptureCode` メタ付き annotation class から動的に
 *   構築された marker FqN 集合) を読み、その marker が付いた **宣言** を IR 走査で発見する
 *   (task-012 で property / class / object / function / typealias の 5 種に拡張)
 * - 発見したサイトを [CapturedSite] にして [K2000CapturedSourcesTransformer.capturedSites] に蓄積
 * - `capturedSources<T>()` 呼び出しを `listOf(T(source = Source(...)))` へ書き換える
 *   (filler 未指定 marker の場合は 0-arg `T()` の list literal)
 *
 * 詳細な順序は `compiler-plugin-design.md` §6 Phase ordering 参照。
 */
public class K2000IrInjector : IrInjector {
    override fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        // ## task-013 で導入した 2 パス transform
        //
        // パス 1 (collect): 全 IrFile を順に走査して `capturedSites` を蓄積する。
        // パス 2 (rewrite): 全 IrFile を transform して `capturedSources<T>()` を list literal に書き換える。
        //
        // **このパス分離が必要な理由**: marker と use site が異なる file にある場合
        // (例: ケース24: marker は `case24/FileA.kt`、`capturedSources<Snippets_Case24>()` の
        // 呼び出しは `BasicCasesTest.kt`)、1 パスで `visitFile` → `visitCall` の順に走らせると、
        // `BasicCasesTest.kt` の rewrite を行う時点で `case24/FileB.kt` の use site が未収集に
        // なる可能性がある。
        // 2 パスにすることで rewrite phase では capturedSites が **module 全体のスナップショット** に
        // なっていることが保証される。
        val transformer = K2000CapturedSourcesTransformer(pluginContext, config)

        // パス 1: 全 file を visit して capturedSites を埋める (rewrite はしない)。
        for (file in moduleFragment.files) {
            val collector = K2000CapturedSourcesCollector(file, config)
            file.acceptChildrenVoid(collector)
            transformer.capturedSites += collector.capturedSites
        }

        // パス 2: 全 file を transform して capturedSources<T>() を書き換える。
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
 * 責務:
 * - [visitCall] で `capturedSources<T>()` を検出し、[capturedSites] から組み立てた
 *   `listOf(T(...))` の [IrCall] に置換する (Logic H)
 *
 * **collect は本 transformer の責務ではない**。task-013 で `K2000IrInjector.transform` が
 * 2 パス構成 (パス 1: 全 file collect → パス 2: rewrite) に変わり、collect は [K2000IrInjector]
 * 側で行うようになった。本 transformer は [capturedSites] を **外部から populate された
 * read-only スナップショット** として扱う。
 *
 * Phase 2 で marker 多型化に伴い、内部 storage を `Map<MarkerFqn, List<CapturedSite>>` に
 * 昇格させる選択肢もあるが、各サイトが `markerFqn` を保持する現行モデルで `filter` するだけでも
 * O(N×M) で実用上問題ないため、Phase 2 中盤に再検討する。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic B/C/D/H、§6 Phase ordering 参照。
 */
internal class K2000CapturedSourcesTransformer(
    private val pluginContext: IrPluginContext,
    private val config: CaptureCodePluginConfig,
) : IrElementTransformerVoid() {

    /**
     * collector が収集した capture サイトの一覧。[visitCall] の書き換え step が参照する。
     *
     * task-013 で transform 2 パス化に伴い、本 list は **外部 ([K2000IrInjector]) から populate
     * されるスナップショット** として運用する。1 パス目で全 file 分が populate された状態で
     * 2 パス目の rewrite が始まる契約。
     *
     * task-012 で declaration 全 5 種別 (property / class / object / function / typealias) に拡張済。
     * task-016 (file annotation) / task-017 (expression annotation) で残りの 2 種を追加予定。
     */
    val capturedSites: MutableList<CapturedSite> = mutableListOf()

    override fun visitCall(expression: IrCall): IrExpression {
        // 子要素を先に処理 (nested capturedSources<T>() 呼び出しに備える)
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed

        if (!transformed.isCapturedSourcesCall()) return transformed
        val markerFqn = transformed.markerTypeArgumentOrNull() ?: return transformed

        val rewritten = K2000CapturedSourcesRewriter.rewriteCapturedSourcesCall(
            original = transformed,
            markerFqn = markerFqn,
            sites = capturedSites.filter { it.markerFqn == markerFqn },
            pluginContext = pluginContext,
            config = config,
        )
        return rewritten ?: transformed
    }

    private fun IrCall.isCapturedSourcesCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == K2000CapturedSourcesRewriter.CAPTURED_SOURCES_FQN

    /**
     * type argument が [CaptureCodeMarkerRegistry] に登録済の marker であれば、その FqN を返す。
     * registry に無い (= `@CaptureCode` メタ付きでない) 場合は `null` を返し、書き換えをスキップする。
     */
    private fun IrCall.markerTypeArgumentOrNull(): String? {
        val typeArg = getTypeArgument(0) ?: return null
        val fqn = typeArg.classFqName?.asString() ?: return null
        return fqn.takeIf { CaptureCodeMarkerRegistry.isMarker(it) }
    }
}
