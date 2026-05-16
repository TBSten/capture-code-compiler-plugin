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
 * 不足) は対応する `resolve(pluginContext)` factory が `null` を返し、 呼び出し側が
 * フォールバックを担う。
 *
 * ## ユーザ定義パラメータとの境界
 *
 * filler は **型** で識別する。 marker constructor の `IrValueParameter` の type が `Source` /
 * `SourceLocation` / `CaptureKind` のいずれかと等しい場合のみ [BuildFiller] に dispatch する。
 * それ以外のユーザ定義パラメータ (e.g. `id: Id` / `label: String`) は別経路
 * ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs.BuildUserArg]
 * 等) で値を埋める。
 *
 * ## drift とのトレードオフ
 *
 * 実装本体 (IR 構築) は K2.0 baseline では `IrConstructorCallImpl.fromSymbolOwner` /
 * `putValueArgument` 等の drift を受けるため、 各 compat-kXXX 側に **concrete impl** を残す。
 * main 側の interface は signature のみを公開し、 task-124+ で main 側に集約することを目標。
 */
public interface BuildFiller {

    /**
     * 与えられた [site] (= 1 件分のキャプチャ情報) から、 filler annotation instance を表す
     * [IrExpression] を生成する。
     *
     * 出力は通常 `IrConstructorCallImpl` (filler annotation の constructor call) になる。
     */
    public operator fun invoke(
        site: CapturedSite,
        config: CaptureCodePluginConfig,
    ): IrExpression
}
