package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.effectiveFor
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName

/**
 * annotation list から、 [CaptureCodeMarkerRegistry] に登録済の marker FqN と、 対応する
 * [IrConstructorCall] をペアで返す。
 *
 * 同一 declaration / file に複数 marker (`@Foo @Bar`) が付いている場合は **すべての** marker を
 * 返す (= 複数 marker 同時 capture をサポート、 各 marker ごとに 1 件ずつ [CollectedSite] が
 * 生成される)。
 *
 * Phase 3a 移植元: 各 `K{XXX}CapturedSourcesCollector.markerAnnotations()` (compat-k200 〜 k240
 * で重複)。
 *
 * ## Preconditions
 *
 * Caller (= [collectIfMarked] / [collectFileAnnotations]) は以下を保証する責務がある。 違反時は
 * 当該 annotation を silent continue するだけで、 他 annotation の処理に影響しないため
 * `require(...)` での fail-fast は導入していない。
 *
 * - receiver `List<IrConstructorCall>` の各要素は IR resolution 完了済 (= `annotation.type` が
 *   解決可能)。 typical root cause: caller が resolution 完了前の annotation を渡している (=
 *   phase 順序 bug)。
 * - 各 `annotation.type.classFqName` が `null` を返すケース (= 未解決 type) は silent continue
 *   で skip。 通常 build では発生しない (IR phase は全 type resolved が保証)。
 * - [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   は FIR phase 完了後の状態。 marker 未登録 fqn は `isMarker` filter で silent skip。
 */
internal fun List<IrConstructorCall>.markerAnnotations(): List<Pair<String, IrConstructorCall>> {
    val result = mutableListOf<Pair<String, IrConstructorCall>>()
    for (annotation in this) {
        val fqn = annotation.type.classFqName?.asString() ?: continue
        if (CaptureCodeMarkerRegistry.isMarker(fqn)) result += fqn to annotation
    }
    return result
}

/**
 * marker FqN に対する effective [CaptureCodePluginConfig] を返す。
 *
 * 同一 marker が複数 site で出現しても `effectiveFor` 計算は 1 回に抑える (キャッシュ機能)。
 * marker に override が無い場合は global config と同一インスタンスが返る
 * ([me.tbsten.capture.code.feature.markerDefinition.effectiveFor] の fast-path)。
 */
internal fun CollectFileContext.effectiveConfigFor(markerFqn: String): CaptureCodePluginConfig =
    effectiveConfigCache.getOrPut(markerFqn) {
        config.effectiveFor(CaptureCodeMarkerRegistry.markerOptionsFor(markerFqn))
    }
