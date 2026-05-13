package me.tbsten.capture.code.compat

import me.tbsten.capture.code.CaptureCodePluginConfig
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * バージョン固有の IR 変換ロジックを表すインターフェース。
 * 各 compat-kXXXX モジュールが実装し、`META-INF/services` 経由で動的にロードされる。
 *
 * task-013 で `config: CaptureCodePluginConfig` を受け取る signature に拡張。filler 自動値埋め
 * と source 正規化 ([Logic D] = `SourceNormalizer.normalize`) が config の `dedent` /
 * `includeAnnotationLines` 等を消費するため。default 値はないので、呼び出し側 (compiler-plugin
 * main module の `CaptureCodeIrExtension`) は必ず config を渡すこと。テスト用に `DEFAULT` を
 * 渡したい場合は [CaptureCodePluginConfig.DEFAULT] を利用する。
 */
public interface IrInjector {
    public fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    )

    /**
     * ServiceLoader で発見される Factory。`minVersion` 以上の Kotlin バージョンで利用可能。
     * 例えば `minVersion = "2.0.0"` の Factory は Kotlin 2.0.0 以降で選択候補になる。
     */
    public interface Factory {
        public val minVersion: String
        public fun create(): IrInjector
    }
}
