package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * `me.tbsten.capture.code.Source(value: String)` filler の値を IR で構築する [BuildFiller]
 * 実装 placeholder。
 *
 * task-120 (IR logic 移行) で旧 `SourceFillerBuilder` (各 `compat-kXXX/filler/`) を rename し、
 * directory structure を main module 側にミラーした版。
 *
 * ## 責務分担
 *
 * IR 構築 API drift により **IR 構築本体は引き続き compat-kXXX 側に残す**
 * (`compat-kXXX/filler/SourceFillerBuilder.kt`)。 normalize 済の `site.source` をそのまま使う部分
 * (= pure) は [me.tbsten.capture.code.feature.capturedSources.ir.normalize.NormalizeSource]
 * 経由で task-118 / task-120 にて main module に集約済。
 *
 * 本 class は task-120-B で IR 構築 drift を CompatContext 経由で吸収できた時点で
 * `BuildFiller` を implement する concrete impl を担う予定。
 */
internal class FillSource : BuildFiller {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 task-120-B で IR drift 吸収後に
     * concrete impl を埋める。
     */
    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
