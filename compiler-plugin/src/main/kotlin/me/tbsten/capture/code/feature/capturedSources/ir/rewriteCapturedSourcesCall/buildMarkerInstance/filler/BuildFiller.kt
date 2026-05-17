package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * filler 自動値埋めの抽象 interface。
 *
 * marker annotation の constructor 引数のうち、 library 提供の filler 型 (`Source` /
 * `SourceLocation` / `CaptureKind`) で宣言されているものに対し、 [CapturedSite] から
 * 該当する値を生成して [IrExpression] を返す責務を持つ。
 *
 * task-120 で旧 `FillerBuilder` interface を rename + invoke 化したもの。
 *
 * ## 実装
 *
 * - [FillCaptureKind] — `CaptureKind(value = Kind.XXX)` を構築
 * - [FillSource] — `Source(value = "...")` を構築
 * - [FillSourceLocation] — `SourceLocation(packageName, filePath, startLine, endLine)` を構築
 *
 * すべての実装は constructor で **必要な IR symbol を resolve 済み** で保持する。 これにより
 * `invoke` 呼び出しは hot path として軽量に保てる。 symbol 解決に失敗した場合 (= runtime 依存が
 * 不足) は対応する `resolveOrNull(pluginContext, compat)` factory が `null` を返し、 呼び出し側
 * ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance])
 * が当該 marker 全体の書き換えを skip する。
 *
 * ## ユーザ定義パラメータとの境界
 *
 * filler は **型** で識別する。 marker constructor の `IrValueParameter` の type が `Source` /
 * `SourceLocation` / `CaptureKind` のいずれかと等しい場合のみ [BuildFiller] に dispatch する。
 * それ以外のユーザ定義パラメータ (e.g. `id: Id` / `label: String`) は別経路
 * ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArg]
 * 等) で値を埋める。
 *
 * ## drift とのトレードオフ (task-120-B Phase 4b 時点)
 *
 * `IrConstructorCallImpl.fromSymbolOwner` / `putValueArgument` / `valueParameters` 等の K2.4-RC
 * 削除 API drift は task-120-B Phase 2 で [me.tbsten.capture.code.compat.CompatContext] SPI
 * (`newIrConstructorCall` / `putCallValueArgument` / `valueParametersOf`) に吸収済。 `IrConstImpl`
 * / `IrGetEnumValueImpl` 等の factory は K2.0 - K2.4-RC 全 baseline で参照可能なため main 直接
 * 呼び出している。 旧 `compat-kXXX` 配下の `SourceFillerBuilder` / `SourceLocationFillerBuilder` /
 * `CaptureKindFillerBuilder` は runtime path として並行存続し、 Phase 5 で `transformIr` を main
 * 経由に切り替えた時点で本 interface が runtime path になる (Phase 6 で旧 builder 削除予定)。
 */
internal interface BuildFiller {

    /**
     * 与えられた [site] (= 1 件分のキャプチャ情報) から、 filler annotation instance を表す
     * [IrExpression] を生成する。
     *
     * 出力は通常 `IrConstructorCallImpl` (filler annotation の constructor call) になる。
     */
    operator fun invoke(
        site: CapturedSite,
        config: CaptureCodePluginConfig,
    ): IrExpression
}
