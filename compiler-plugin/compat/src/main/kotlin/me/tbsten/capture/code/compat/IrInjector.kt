package me.tbsten.capture.code.compat

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * バージョン固有の IR 変換ロジックを表すインターフェース。
 * 各 compat-kXXXX モジュールが実装し、`META-INF/services` 経由で動的にロードされる。
 */
public interface IrInjector {
    public fun transform(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext)

    /**
     * ServiceLoader で発見される Factory。`minVersion` 以上の Kotlin バージョンで利用可能。
     * 例えば `minVersion = "2.0.0"` の Factory は Kotlin 2.0.0 以降で選択候補になる。
     */
    public interface Factory {
        public val minVersion: String
        public fun create(): IrInjector
    }
}
