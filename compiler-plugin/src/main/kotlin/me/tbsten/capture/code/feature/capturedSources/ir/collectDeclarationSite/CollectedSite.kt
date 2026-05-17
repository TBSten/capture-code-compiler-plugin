package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall

/**
 * `CollectDeclarationSite()` が収集した 1 件分のキャプチャ情報。
 *
 * 公開 API である [CapturedSite] に加えて、 marker annotation の [IrConstructorCall] と
 * EXPRESSION 起源の user args / 当該 site に対する effective config を保持する。 これは
 * 後段 ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall])
 * が marker instance を組み立てる際に必要な情報をまとめて運ぶための内部受け渡し型 (= main
 * module 内部 SSoT)。
 *
 * task-120-B Phase 3a で `K{XXX}CapturedSiteData` (各 compat-kXXX で重複定義されていた
 * data class) を本 type に集約。 Phase 4-5 で各 compat-kXXX の collector が wire を本 type に
 * 切り替え、 Phase 6 で `K{XXX}CapturedSiteData` は削除予定。
 *
 * ## 各フィールドの意味
 *
 * @property site 公開 API としての site 情報 ([CapturedSite])
 * @property markerCall `@Marker(...)` 自身に対応する IR コンストラクタ呼び出し。
 *   - declaration / file 起源 — 必ず non-null (IR phase に annotation が残る)
 *   - EXPRESSION 起源 — 必ず `null` (Kotlin 2.0 では IR phase に式 annotation が残らない)
 * @property expressionUserArgs EXPRESSION 起源で、 FIR session storage から取り出した
 *   「filler 以外」の parameter 値 (name → primitive / enum FqN)。 declaration / file 起源では
 *   `markerCall` から直接 IR 式が取れるので **空 Map**。
 * @property effectiveConfig 当該 site に対して計算済の effective config (= global Gradle DSL
 *   config に marker 単位の override
 *   [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions] を適用した結果)。
 *   marker に override が無い場合は global config と同一インスタンスになる
 *   ([me.tbsten.capture.code.feature.markerDefinition.effectiveFor] の fast-path)。
 */
public data class CollectedSite(
    public val site: CapturedSite,
    public val markerCall: IrConstructorCall?,
    public val expressionUserArgs: Map<String, Any?> = emptyMap(),
    public val effectiveConfig: CaptureCodePluginConfig,
)
