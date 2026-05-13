package me.tbsten.capture.code

import me.tbsten.capture.code.compat.IrInjectorLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Capture Code compiler plugin の IR 拡張点。
 * 現在の Kotlin バージョンに合った [me.tbsten.capture.code.compat.IrInjector] を選択して委譲する。
 */
public class CaptureCodeIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val injector = IrInjectorLoader.load(KotlinCompilerVersion.VERSION)
        injector.transform(moduleFragment, pluginContext)
    }
}
