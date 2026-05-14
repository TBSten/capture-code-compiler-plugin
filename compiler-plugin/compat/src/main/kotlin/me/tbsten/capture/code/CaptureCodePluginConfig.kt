package me.tbsten.capture.code

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Capture Code compiler plugin の plugin option を表す immutable な SSOT data class。
 *
 * Gradle DSL (`CaptureCodeExtension`) や `CaptureCodeCommandLineProcessor` 経由で受け取った値を
 * このクラスに集約し、`CompilerConfiguration` に [CAPTURE_CODE_PLUGIN_CONFIG_KEY] で詰める。
 * FIR / IR extension 側からは [CompilerConfiguration.captureCodePluginConfig] で取り出し、
 * 各 Logic (D: source 正規化、F: marker checker、H: capturedSources 書き換え など) で参照する。
 *
 * design `compiler-plugin-design.md` §5 Logic I / §8.5 を参照。
 *
 * ## 配置 (task-013 で `:compiler-plugin` から `:compiler-plugin:compat` へ移動)
 *
 * task-018 では本 module は `:compiler-plugin/main` 配下にあったが、task-013 で `:compat-k2000`
 * の IR transformer が config を消費する必要が出たため、`:compat` モジュールへ物理移動した。
 * package は `me.tbsten.capture.code` のまま維持しているので、`:compiler-plugin` から
 * 参照する import path は変更不要 (`me.tbsten.capture.code.CaptureCodePluginConfig`)。
 *
 * @property includeKdoc キャプチャしたソースに KDoc コメントを残すかどうか。
 *                       デフォルト `true`。実消費は task-015 (Logic D) 以降で行う。
 * @property includeImports file 起源 (`@file:Marker`) のキャプチャで `import` 宣言行を含めるか。
 *                          デフォルト `false` (= `package` / `import` 行を除外)。task-016 で
 *                          `K200CapturedSourcesCollector.fileNormalizeOptions` 経由で
 *                          `NormalizeOptions.stripPackageAndImport = !includeImports` に投影される。
 * @property includeAnnotationLines 宣言の先頭に並ぶ `@Marker` annotation 行をキャプチャに含めるか。
 *                                  デフォルト `false`。実消費は task-013 / task-015 で行う。
 * @property dedent 全行の最小インデント幅 (空白行を除く) を計算し各行から削除するかどうか。
 *                  デフォルト `true`。実消費は task-015 (Logic D) で行う。
 * @property includeLineInfo `SourceLocation.startLine` / `endLine` を実値で埋めるかどうか。
 *                           デフォルト `true` (design §11 open question #1 は `true` 採用、Phase 5 で再評価)。
 */
public data class CaptureCodePluginConfig(
    val includeKdoc: Boolean = true,
    val includeImports: Boolean = false,
    val includeAnnotationLines: Boolean = false,
    val dedent: Boolean = true,
    val includeLineInfo: Boolean = true,
) {
    public companion object {
        /** すべての option が design 既定値の状態。CommandLineProcessor の初期 base にも使う。 */
        public val DEFAULT: CaptureCodePluginConfig = CaptureCodePluginConfig()
    }
}

/**
 * `CompilerConfiguration` に [CaptureCodePluginConfig] を格納するための key。
 *
 * CommandLineProcessor が 5 つの CLI option を集約してこの key に `put` する。
 * FIR / IR extension からは [captureCodePluginConfig] で取り出す (見つからない場合は
 * [CaptureCodePluginConfig.DEFAULT] にフォールバック)。
 */
public val CAPTURE_CODE_PLUGIN_CONFIG_KEY: CompilerConfigurationKey<CaptureCodePluginConfig> =
    CompilerConfigurationKey.create("capture-code plugin config")

/**
 * `CompilerConfiguration` から [CaptureCodePluginConfig] を取り出す。
 *
 * CommandLineProcessor 未配線 / option 未指定の場合は [CaptureCodePluginConfig.DEFAULT] を返す。
 */
public val CompilerConfiguration.captureCodePluginConfig: CaptureCodePluginConfig
    get() = this[CAPTURE_CODE_PLUGIN_CONFIG_KEY] ?: CaptureCodePluginConfig.DEFAULT
