package me.tbsten.capture.code.compat.k202

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.compat.CapturedSite
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Kotlin 2.0.x 向けの IR 変換エントリ。 [CompatContextImpl.transformIr] から呼ばれる。
 *
 * Logic A (動的 marker 検出) 後の責務:
 * - [CaptureCodeMarkerRegistry] (FIR phase で `@CaptureCode` メタ付き annotation class から動的に
 *   構築された marker FqN 集合) を読み、 その marker が付いた **宣言** を IR 走査で発見する
 *   (property / class / object / function / typealias の 5 種に対応)
 * - 発見したサイトを [CapturedSite] にして [K202CapturedSourcesTransformer.capturedSiteData] に蓄積
 * - `capturedSources<T>()` 呼び出しを `listOf(T(source = Source(...)))` へ書き換える
 *   (filler 未指定 marker の場合は 0-arg `T()` の list literal)
 *
 * 単一の Metro pattern interface (`CompatContext.transformIr`) に統合された結果、 本関数は単独
 * entry point として呼ばれる。 内部の 2 パス transform (collect → rewrite) ロジックはそのまま。
 *
 * 詳細な順序は `compiler-plugin-design.md` §6 Phase ordering 参照。
 */
internal fun runK202IrTransform(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    config: CaptureCodePluginConfig,
) {
    // ## 2 パス transform
    //
    // パス 1 (collect): 全 IrFile を順に走査して `capturedSites` を蓄積する。
    // パス 2 (rewrite): 全 IrFile を transform して `capturedSources<T>()` を list literal に書き換える。
    //
    // **このパス分離が必要な理由**: marker と use site が異なる file にある場合
    // (例: marker class が file A、 `capturedSources<MyMarker>()` の呼び出しが file B、
    //  marker 付き use site が file C)、 1 パスで `visitFile` → `visitCall` の順に走らせると、
    // file B の rewrite を行う時点で file C の use site が未収集になる可能性がある。
    // 2 パスにすることで rewrite phase では capturedSites が **module 全体のスナップショット** に
    // なっていることが保証される。
    val transformer = K202CapturedSourcesTransformer(pluginContext, config)

    // パス 1: 全 file を visit して capturedSiteData を埋める (rewrite はしない)。
    for (file in moduleFragment.files) {
        val collector = K202CapturedSourcesCollector(file, config)
        collector.collectFileAnnotations()
        file.acceptChildrenVoid(collector)
        collector.collectExpressionSites()
        transformer.capturedSiteData += collector.capturedSiteData
    }

    // パス 2: 全 file を transform して capturedSources<T>() を書き換える。
    moduleFragment.transformChildrenVoid(transformer)
}

/**
 * IR を走査して `@CaptureCode` 由来のキャプチャを構築する transformer。
 *
 * 責務:
 * - [visitCall] で `capturedSources<T>()` を検出し、 [capturedSiteData] から組み立てた
 *   `listOf(T(...))` の [IrCall] に置換する (Logic H)
 *
 * **collect は本 transformer の責務ではない**。 transform は 2 パス構成 (パス 1: 全 file collect →
 * パス 2: rewrite) で、 collect は [runK202IrTransform] 側で行う。 本 transformer は
 * [capturedSiteData] を **外部から populate された read-only スナップショット** として扱う。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic B/C/D/H、 §6 Phase ordering 参照。
 */
internal class K202CapturedSourcesTransformer(
    private val pluginContext: IrPluginContext,
    private val config: CaptureCodePluginConfig,
) : IrElementTransformerVoid() {

    val capturedSiteData: MutableList<K202CapturedSiteData> = mutableListOf()

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed

        if (!transformed.isCapturedSourcesCall()) return transformed
        val markerFqn = transformed.markerTypeArgumentOrNull() ?: return transformed

        val rewritten = K202CapturedSourcesRewriter.rewriteCapturedSourcesCall(
            original = transformed,
            markerFqn = markerFqn,
            siteData = capturedSiteData.filter { it.site.markerFqn == markerFqn },
            pluginContext = pluginContext,
            config = config,
        )
        return rewritten ?: transformed
    }

    private fun IrCall.isCapturedSourcesCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == K202CapturedSourcesRewriter.CAPTURED_SOURCES_FQN

    /**
     * type argument が [CaptureCodeMarkerRegistry] に登録済の marker であれば、 その FqN を返す。
     * registry に無い (= `@CaptureCode` メタ付きでない) 場合は `null` を返し、 書き換えをスキップする。
     */
    private fun IrCall.markerTypeArgumentOrNull(): String? {
        val typeArg = getTypeArgument(0) ?: return null
        val fqn = typeArg.classFqName?.asString() ?: return null
        return fqn.takeIf { CaptureCodeMarkerRegistry.isMarker(it) }
    }
}
