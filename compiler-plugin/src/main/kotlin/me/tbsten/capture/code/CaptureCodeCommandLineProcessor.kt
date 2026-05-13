package me.tbsten.capture.code

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Capture Code compiler plugin の CLI option エントリポイント。
 *
 * Gradle plugin (`CaptureCodeGradlePlugin`) が `SubpluginOption` 経由で渡してきた値を受け取り、
 * 5 つの option を [CaptureCodePluginConfig] に集約して [CompilerConfiguration] に詰める。
 * FIR / IR extension は `CompilerConfiguration.captureCodePluginConfig` で取得する。
 *
 * design `compiler-plugin-design.md` §5 Logic I / §8.5 参照。
 */
@AutoService(CommandLineProcessor::class)
public class CaptureCodeCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = CAPTURE_CODE_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = OPTION_INCLUDE_KDOC,
            valueDescription = "<true|false>",
            description = "Include KDoc comments in the captured source",
            required = false,
        ),
        CliOption(
            optionName = OPTION_INCLUDE_IMPORTS,
            valueDescription = "<true|false>",
            description = "Include import declaration lines for file-level captures",
            required = false,
        ),
        CliOption(
            optionName = OPTION_INCLUDE_ANNOTATION_LINES,
            valueDescription = "<true|false>",
            description = "Include leading @Marker annotation lines in the captured source",
            required = false,
        ),
        CliOption(
            optionName = OPTION_DEDENT,
            valueDescription = "<true|false>",
            description = "Strip the common leading indent from each line",
            required = false,
        ),
        CliOption(
            optionName = OPTION_INCLUDE_LINE_INFO,
            valueDescription = "<true|false>",
            description = "Populate SourceLocation.startLine / endLine with real line numbers",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        val current = configuration.captureCodePluginConfig
        val updated = when (option.optionName) {
            OPTION_INCLUDE_KDOC -> current.copy(includeKdoc = value.toBoolean())
            OPTION_INCLUDE_IMPORTS -> current.copy(includeImports = value.toBoolean())
            OPTION_INCLUDE_ANNOTATION_LINES -> current.copy(includeAnnotationLines = value.toBoolean())
            OPTION_DEDENT -> current.copy(dedent = value.toBoolean())
            OPTION_INCLUDE_LINE_INFO -> current.copy(includeLineInfo = value.toBoolean())
            else -> error("Unknown plugin option: ${option.optionName}")
        }
        configuration.put(CAPTURE_CODE_PLUGIN_CONFIG_KEY, updated)
    }

    public companion object {
        public const val OPTION_INCLUDE_KDOC: String = "includeKdoc"
        public const val OPTION_INCLUDE_IMPORTS: String = "includeImports"
        public const val OPTION_INCLUDE_ANNOTATION_LINES: String = "includeAnnotationLines"
        public const val OPTION_DEDENT: String = "dedent"
        public const val OPTION_INCLUDE_LINE_INFO: String = "includeLineInfo"
    }
}

public const val CAPTURE_CODE_PLUGIN_ID: String = "me.tbsten.capture.code"
