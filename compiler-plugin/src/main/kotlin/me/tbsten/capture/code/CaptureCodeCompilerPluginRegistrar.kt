package me.tbsten.capture.code

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@AutoService(CompilerPluginRegistrar::class)
public class CaptureCodeCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val config = configuration.captureCodePluginConfig
        FirExtensionRegistrarAdapter.registerExtension(CaptureCodeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(CaptureCodeIrExtension(config))
    }
}
