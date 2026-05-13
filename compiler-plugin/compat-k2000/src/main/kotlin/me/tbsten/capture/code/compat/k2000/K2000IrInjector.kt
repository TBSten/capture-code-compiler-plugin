package me.tbsten.capture.code.compat.k2000

import com.google.auto.service.AutoService
import me.tbsten.capture.code.compat.IrInjector
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Kotlin 2.0.0+ 向けの IR 変換実装 (ダミー)。
 *
 * TODO: 実際の Capture Code 変換ロジックは後で実装。
 *   IR フェーズで `me.tbsten.capture.code.captureCode()` を捕捉し、ソースを差し替える想定。
 */
public class K2000IrInjector : IrInjector {
    override fun transform(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // TODO: IR 変換実装
    }

    @AutoService(IrInjector.Factory::class)
    public class Factory : IrInjector.Factory {
        override val minVersion: String = "2.0.0"
        override fun create(): IrInjector = K2000IrInjector()
    }
}
