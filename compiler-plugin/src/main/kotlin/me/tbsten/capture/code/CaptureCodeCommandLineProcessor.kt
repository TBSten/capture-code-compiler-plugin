package me.tbsten.capture.code

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

@AutoService(CommandLineProcessor::class)
public class CaptureCodeCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = CAPTURE_CODE_PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}

public const val CAPTURE_CODE_PLUGIN_ID: String = "me.tbsten.capture.code"
