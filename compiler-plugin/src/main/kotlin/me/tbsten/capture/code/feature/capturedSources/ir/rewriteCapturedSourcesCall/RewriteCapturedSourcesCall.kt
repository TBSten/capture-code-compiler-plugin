package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * Logic H: `capturedSources<T>()` 呼び出しを `listOf(T(...))` に書き換える logic。
 *
 * task-120-B Phase 4a で concrete 化。 これまで各 `compat-kXXX/K{XXX}CapturedSourcesRewriter.kt`
 * + `K{XXX}IrTransform.kt` (transformer 部分) に重複していた 「`capturedSources<T>()` 検出 →
 * marker FqN 抽出 → site 集約 → marker instance 列構築」 の orchestrator 部分を main module 1
 * 箇所に集約した版。 IR 構築本体 (= IR drift) は [CompatContext] の Phase 2 で追加した 11 IR
 * primitive 経由で吸収する。
 *
 * ## 責務
 *
 * - [invoke] が module 全体を走査して `capturedSources<T>()` の各 [IrCall] を transform
 * - call が `me.tbsten.capture.code.capturedSources` (CallableId 一致) かつ T 型引数が
 *   registered marker の場合のみ [BuildMarkerInstance] に dispatch して `listOf(T(...))` で
 *   置換する
 * - T が registered marker ではない (= 未登録の type argument) 場合は no-op (元の call をそのまま
 *   残す) → 実行時は runtime API の stub `listOf()` がそのまま返る
 * - module 全体 walk は [CompatContext.transformCallsInModule] (Phase 2 SPI) に委譲し、 IR
 *   transformer 基底 class の drift を吸収する
 *
 * ## 旧構造との関係 (Phase 4a 時点)
 *
 * 既存の `K{XXX}CapturedSourcesRewriter` + `K{XXX}IrTransform` の transformer 部分は **並行存在**
 * する。 Phase 5 で `transformIr` 経由の wiring を main 経由に切り替えるまで、 既存 path が runtime
 * path として残り続け、 本 class は caller 0 件 (= dead code) のまま。 既存 test は引き続き compat-kXXX
 * 経路で PASS する想定。 Phase 6 で各 compat-kXXX の旧 transformer を削除する。
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 [BuildMarkerInstance] と組み合わせて使うため、 invoke は state を
 * 持たない pure method として呼べる (`RewriteCapturedSourcesCall()(...)`)。
 */
public class RewriteCapturedSourcesCall {

    /**
     * moduleFragment 全体を走査し、 各 `capturedSources<T>()` 呼び出しのうち T が registered marker
     * のものを `listOf(T(site1), T(site2), ...)` に置換する。
     *
     * 1. [CompatContext.transformCallsInModule] 経由で全 `IrCall` を visit
     * 2. 各 call について [isCapturedSourcesCall] で `me.tbsten.capture.code.capturedSources` 判定
     * 3. type argument T を [markerFqnOf] で抽出 — registered marker なら FqN、 そうでなければ null
     * 4. marker FqN ごとに [collectedSites] を filter し、 [BuildMarkerInstance] で `listOf(T(...))`
     *    の [IrExpression] を構築
     * 5. 構築結果を transformer に返して原 call を置換 (null return で no-op)
     *
     * @param moduleFragment IR transform 対象の moduleFragment
     * @param pluginContext IrPluginContext (BuildMarkerInstance が marker class / constructor /
     *   listOf symbol を resolve するために使う)
     * @param compat IR primitive (`transformCallsInModule`, `setCallTypeArgument` 等) を委譲する SPI
     * @param config global Gradle DSL config (per-marker effective config は [CollectedSite] が保持)
     * @param collectedSites Phase 3a の [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite]
     *   の戻り値。 module 全体から収集した site の snapshot
     */
    public operator fun invoke(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        config: CaptureCodePluginConfig,
        collectedSites: List<CollectedSite>,
    ) {
        val buildMarker = BuildMarkerInstance()
        compat.transformCallsInModule(moduleFragment) { call ->
            if (!call.isCapturedSourcesCall()) return@transformCallsInModule null
            val markerFqn = call.markerFqnOf(compat) ?: return@transformCallsInModule null
            val sitesForMarker = collectedSites.filter { it.site.markerFqn == markerFqn }
            buildMarker(
                call = call,
                markerFqn = markerFqn,
                sites = sitesForMarker,
                pluginContext = pluginContext,
                compat = compat,
                config = config,
            )
        }
    }

    /**
     * `capturedSources<T>()` 呼び出しの type argument T が registered marker かを判定する helper。
     *
     * Phase 4a 以降 `RewriteCapturedSourcesCall.invoke` の中だけで使うが、 単独テスト可能性のため
     * `internal` で残す (= caller がいなくても unit test だけ可能)。
     */
    internal fun isRegisteredMarker(typeArgumentFqn: String?): Boolean {
        if (typeArgumentFqn == null) return false
        return CaptureCodeMarkerRegistry.isMarker(typeArgumentFqn)
    }

    /**
     * call の callee が `me.tbsten.capture.code.capturedSources` (= 書き換え対象) かを判定する。
     *
     * 判定は **owner function の FqN** で行う ([CaptureCodeCallableIds.capturedSources] の SSoT を
     * 1 度だけ asSingleFqName().asString() で文字列化し、 後段 `IrSimpleFunction.fqNameWhenAvailable`
     * と一致比較)。 これは各 `compat-kXXX/K{XXX}CapturedSourcesTransformer` と同じパターンで、
     * 関数の overload や receiver の有無に関係なく一意に判定できる。
     */
    private fun IrCall.isCapturedSourcesCall(): Boolean =
        symbol.owner.fqNameWhenAvailable?.asString() == CAPTURED_SOURCES_FQN

    /**
     * type argument 0 (= `capturedSources<T>()` の T) を取り出し、 [CaptureCodeMarkerRegistry] に
     * 登録された marker FqN ならそれを返し、 そうでなければ null。
     *
     * `compat.getCallTypeArgument` (Phase 2 SPI) 経由で K2.4-RC 削除 API drift を吸収する。
     */
    private fun IrCall.markerFqnOf(compat: CompatContext): String? {
        val typeArg = compat.getCallTypeArgument(this, 0) ?: return null
        val fqn = typeArg.classFqName?.asString() ?: return null
        return fqn.takeIf { CaptureCodeMarkerRegistry.isMarker(it) }
    }

    private companion object {
        /**
         * 書き換え対象 `capturedSources<T>()` の完全修飾名。 main module の SSoT
         * [CaptureCodeCallableIds.capturedSources] から派生 (task-091 以降 SSoT 必須)。
         */
        private val CAPTURED_SOURCES_FQN: String =
            CaptureCodeCallableIds.capturedSources.asSingleFqName().asString()
    }
}
