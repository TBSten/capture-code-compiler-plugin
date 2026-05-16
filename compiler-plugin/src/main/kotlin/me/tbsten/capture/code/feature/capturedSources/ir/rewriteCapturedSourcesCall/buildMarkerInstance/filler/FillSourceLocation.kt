package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * `me.tbsten.capture.code.SourceLocation(packageName, filePath, startLine, endLine)` filler の値を
 * IR で構築する [BuildFiller] 実装 placeholder。
 *
 * task-120 (IR logic 移行) で旧 `SourceLocationFillerBuilder` (各 `compat-kXXX/filler/`) を rename し、
 * directory structure を main module 側にミラーした版。
 *
 * ## 責務分担
 *
 * IR 構築 API drift により **IR 構築本体は引き続き compat-kXXX 側に残す**
 * (`compat-kXXX/filler/SourceLocationFillerBuilder.kt`)。 `config.includeLineInfo = false`
 * 時の line info 0-fill 等の pure 部分は CapturedSite data 経由で main 側で計算済。
 *
 * 本 class は task-120-B で IR 構築 drift を CompatContext 経由で吸収できた時点で
 * `BuildFiller` を implement する concrete impl を担う予定。
 */
public class FillSourceLocation : BuildFiller {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 task-120-B で IR drift 吸収後に
     * concrete impl を埋める。
     */
    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
