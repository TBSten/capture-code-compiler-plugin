package me.tbsten.capture.code

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * `CaptureCodeCommandLineProcessor` の processOption 配線テスト。
 *
 * 6 option × (on / off / 未指定) のすべてが `CaptureCodePluginConfig` に正しく集約され、
 * 未指定時は `CaptureCodePluginConfig.DEFAULT` が使われることを確認する。
 * (task-120-B Phase 7 で `warnOnEmptyCapture` opt-in flag を追加し、 5 → 6 に拡張。)
 *
 * design `compiler-plugin-design.md` §5 Logic I / §8.5 (`CaptureCodePluginConfig` SSOT) を参照。
 *
 * 実装メモ: `CommandLineProcessor` interface には `processOption(AbstractCliOption, ...)` と
 * deprecated な `processOption(CliOption, ...)` の 2 つの overload があり、引数の **静的型**
 * によって dispatch 先が決まる。本テストでは新しい signature が呼ばれるよう、`optionByName` の
 * 戻り値型を [AbstractCliOption] にしておく。
 */
class CommandLineProcessorTest : FunSpec({

    val processor = CaptureCodeCommandLineProcessor()

    // option name -> 該当する AbstractCliOption を返すヘルパ。
    // 戻り値型を AbstractCliOption にすることで、deprecated な processOption(CliOption, ...)
    // ではなく processOption(AbstractCliOption, ...) のほうにディスパッチさせる。
    fun optionByName(name: String): AbstractCliOption =
        processor.pluginOptions.first { it.optionName == name }

    fun processWith(vararg pairs: Pair<String, String>): CaptureCodePluginConfig {
        val configuration = CompilerConfiguration()
        for ((name, value) in pairs) {
            processor.processOption(optionByName(name), value, configuration)
        }
        return configuration.captureCodePluginConfig
    }

    // -----------------------------------------------------------------
    // 未指定 → DEFAULT
    // -----------------------------------------------------------------
    test("未指定時は CaptureCodePluginConfig.DEFAULT が返る") {
        val configuration = CompilerConfiguration()
        configuration.captureCodePluginConfig shouldBe CaptureCodePluginConfig.DEFAULT
    }

    test("pluginOptions は 6 つの option を公開する") {
        val names = processor.pluginOptions.map { it.optionName }.toSet()
        names shouldBe setOf(
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_KDOC,
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_IMPORTS,
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_ANNOTATION_LINES,
            CaptureCodeCommandLineProcessor.OPTION_DEDENT,
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_LINE_INFO,
            CaptureCodeCommandLineProcessor.OPTION_WARN_ON_EMPTY_CAPTURE,
        )
    }

    // -----------------------------------------------------------------
    // 個別 option: on / off で正しく flag が動く
    // -----------------------------------------------------------------
    context("includeKdoc") {
        test("true を渡すと includeKdoc = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_KDOC to "true")
                .includeKdoc shouldBe true
        }
        test("false を渡すと includeKdoc = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_KDOC to "false")
                .includeKdoc shouldBe false
        }
    }

    context("includeImports") {
        test("true を渡すと includeImports = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_IMPORTS to "true")
                .includeImports shouldBe true
        }
        test("false を渡すと includeImports = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_IMPORTS to "false")
                .includeImports shouldBe false
        }
    }

    context("includeAnnotationLines") {
        test("true を渡すと includeAnnotationLines = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_ANNOTATION_LINES to "true")
                .includeAnnotationLines shouldBe true
        }
        test("false を渡すと includeAnnotationLines = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_ANNOTATION_LINES to "false")
                .includeAnnotationLines shouldBe false
        }
    }

    context("dedent") {
        test("true を渡すと dedent = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_DEDENT to "true")
                .dedent shouldBe true
        }
        test("false を渡すと dedent = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_DEDENT to "false")
                .dedent shouldBe false
        }
    }

    context("includeLineInfo") {
        test("true を渡すと includeLineInfo = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_LINE_INFO to "true")
                .includeLineInfo shouldBe true
        }
        test("false を渡すと includeLineInfo = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_INCLUDE_LINE_INFO to "false")
                .includeLineInfo shouldBe false
        }
    }

    context("warnOnEmptyCapture") {
        test("true を渡すと warnOnEmptyCapture = true") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_WARN_ON_EMPTY_CAPTURE to "true")
                .warnOnEmptyCapture shouldBe true
        }
        test("false を渡すと warnOnEmptyCapture = false") {
            processWith(CaptureCodeCommandLineProcessor.OPTION_WARN_ON_EMPTY_CAPTURE to "false")
                .warnOnEmptyCapture shouldBe false
        }
        test("default は false (opt-in)") {
            CaptureCodePluginConfig.DEFAULT.warnOnEmptyCapture shouldBe false
        }
    }

    // -----------------------------------------------------------------
    // 6 つの option を同時に指定すると、すべてが反映される (SSOT 集約)
    // -----------------------------------------------------------------
    test("複数 option を組み合わせると CaptureCodePluginConfig に集約される") {
        val config = processWith(
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_KDOC to "false",
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_IMPORTS to "true",
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_ANNOTATION_LINES to "true",
            CaptureCodeCommandLineProcessor.OPTION_DEDENT to "false",
            CaptureCodeCommandLineProcessor.OPTION_INCLUDE_LINE_INFO to "false",
            CaptureCodeCommandLineProcessor.OPTION_WARN_ON_EMPTY_CAPTURE to "true",
        )
        config shouldBe CaptureCodePluginConfig(
            includeKdoc = false,
            includeImports = true,
            includeAnnotationLines = true,
            dedent = false,
            includeLineInfo = false,
            warnOnEmptyCapture = true,
        )
    }

    test("一部 option のみ指定すると他は DEFAULT のまま") {
        val config = processWith(
            CaptureCodeCommandLineProcessor.OPTION_DEDENT to "false",
        )
        config shouldBe CaptureCodePluginConfig.DEFAULT.copy(dedent = false)
    }
})
