package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry

/**
 * Logic H: `capturedSources<T>()` 呼び出しを `listOf(T(...))` に書き換える logic。
 *
 * task-120 (IR logic 移行) で各 `compat-kXXX/K{XXX}CapturedSourcesRewriter.kt` の構造を
 * main module 側にミラーした版。
 *
 * ## 責務分担 (drift とのトレードオフ)
 *
 * IR 構築 API (`IrCallImpl(...)`, `IrConstructorCallImpl.fromSymbolOwner(...)`,
 * `putValueArgument(i, expr)`, `getValueArgument(i)`, `valueParameters`,
 * `deepCopyWithSymbols()` 等) は K200 / K210 / K220 / K230 / K240rc の各 baseline で
 * シグネチャや存在自体が drift する。 特に K2.4-RC では大量の API が削除されているため、
 * **IR 構築本体は引き続き compat-kXXX 側に残す** (K2.0 baseline で記述された main bytecode
 * から呼び出すと NoSuchMethodError 等で実行時に落ちる)。
 *
 * 本 class は以下の **pure 機能のみ** を提供する:
 * - 書き換え対象判定 (`isMarkerCallableId`)
 * - marker FqN フィルタ (`filterSitesForMarker`)
 *
 * 実際の IR 構築 chain は compat-kXXX 配下の `K{XXX}CapturedSourcesRewriter` が担当し、
 * 本 class の helpers を call out して使う。
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 IR 構築 drift を CompatContext 経由で全部吸収する未来形 (= 本 class の
 * invoke が moduleFragment を受け取って IR を transform する形) は task-124+ の compat slim 化で
 * 実現を目指す。 現状は **directory structure と pure helper の集約** にとどめる。
 */
internal class RewriteCapturedSourcesCall {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 将来 IR 構築 drift を
     * CompatContext 経由で吸収できた段階で、 本 invoke が moduleFragment を transform する
     * orchestrator になる。 task-120-B で concrete impl を埋める。
     */
    internal operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )

    /**
     * `capturedSources<T>()` 呼び出しの type argument T が registered marker かを判定する。
     */
    internal fun isRegisteredMarker(typeArgumentFqn: String?): Boolean {
        if (typeArgumentFqn == null) return false
        return CaptureCodeMarkerRegistry.isMarker(typeArgumentFqn)
    }
}
