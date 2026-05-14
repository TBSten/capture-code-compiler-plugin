package me.tbsten.capture.code.compat.k210.filler

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CapturedSite
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * task-013 で導入された filler 自動値埋めの抽象 interface。
 *
 * marker annotation の constructor 引数のうち、library 提供の filler 型 (`Source` /
 * `SourceLocation` / `CaptureKind`) で宣言されているものに対し、[CapturedSite] から該当する値を
 * 生成して `IrExpression` を返す責務を持つ。
 *
 * design §5 Logic H / §7.9 を参照。
 *
 * ## 実装
 *
 * - [SourceFillerBuilder] — `Source(value = "...")` を構築
 * - [SourceLocationFillerBuilder] — `SourceLocation(packageName, filePath, startLine, endLine)` を構築
 * - [CaptureKindFillerBuilder] — `CaptureKind(value = Kind.XXX)` を構築
 *
 * すべての builder は constructor で **必要な IR symbol を resolve 済み** で保持する。これにより
 * `build` 呼び出しは hot path として軽量に保てる。symbol 解決に失敗した場合 (= runtime 依存が
 * 不足) は [FillerBuilders.resolveAll] が `null` を返し、呼び出し側がフォールバックを担う。
 *
 * ## ユーザ定義パラメータとの境界 (task-014 で拡張)
 *
 * filler は **型** で識別する。marker constructor の `IrValueParameter` の type が `Source` /
 * `SourceLocation` / `CaptureKind` のいずれかと等しい場合のみ [FillerBuilder] に dispatch する。
 * それ以外のユーザ定義パラメータ (e.g. `id: Id` / `label: String`) は task-014 で別経路で値を
 * 埋める (`UserDefinedParameterBuilder` 仮称)。本 builder 群は filler 専用で、ユーザ定義に
 * 関知しない。
 */
internal interface FillerBuilder {

    /**
     * 与えられた [site] (= 1 件分のキャプチャ情報) から、filler annotation instance を表す
     * [IrExpression] を生成する。
     *
     * 出力は通常 `IrConstructorCallImpl` (filler annotation の constructor call) になる。
     */
    fun build(
        site: CapturedSite,
        config: CaptureCodePluginConfig,
    ): IrExpression
}
