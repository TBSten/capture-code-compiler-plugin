package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * `me.tbsten.capture.code.CaptureKind(value: Kind)` filler の値を IR で構築する [BuildFiller]
 * 実装 placeholder。
 *
 * task-120 (IR logic 移行) で旧 `CaptureKindFillerBuilder` (各 `compat-kXXX/filler/`) を rename し、
 * directory structure を main module 側にミラーした版。
 *
 * ## 責務分担
 *
 * `IrConstructorCallImpl.fromSymbolOwner(...)` / `putValueArgument(i, expr)` /
 * `IrGetEnumValueImpl(...)` 等の IR 構築 API は K2.4-RC で削除されているため、 main bytecode から
 * 呼ぶと NoSuchMethodError になる。 そのため **IR 構築本体は引き続き compat-kXXX 側に残す**
 * (`compat-kXXX/filler/CaptureKindFillerBuilder.kt`)。
 *
 * 本 class は task-120-B で IR 構築 drift を CompatContext 経由で吸収できた時点で
 * `BuildFiller` を implement する concrete impl を担う予定。 現状は signature だけ確定させて
 * fail-fast 実装で「未実装の placeholder」 であることを明示する。
 */
public class FillCaptureKind : BuildFiller {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 task-120-B で IR drift 吸収後に
     * concrete impl を埋める。
     */
    override fun invoke(site: CapturedSite, config: CaptureCodePluginConfig): IrExpression =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
