package me.tbsten.capture.code

import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.compat.IrInjectorLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Capture Code compiler plugin の IR 拡張点。
 *
 * 現在の Kotlin バージョンに合った [me.tbsten.capture.code.compat.IrInjector] を選択して委譲する。
 *
 * Logic A (task-008) の動的検出結果は [CaptureCodeMarkerRegistry] (FIR phase の checker が蓄積) を
 * 通じて受け渡されるため、本 extension では FIR session に直接アクセスする必要はない。
 *
 * ## Registry lifecycle
 *
 * [CaptureCodeMarkerRegistry] は plugin module の object として表現された compilation-scoped holder。
 * 同一 JVM (= 同一 ClassLoader) で複数コンパイル (例: kctfork による連続 test compile) が走る場合、
 * 前回の marker FqN が次回に漏れないよう、本 extension の `generate` 完了時に [reset]
 * (try/finally) してクリアする。
 *
 * KMP の複数 target (例: jvmMain + jsMain) は task-024 以降でハンドリングする (本 ticket scope 外)。
 *
 * ## Plugin config
 *
 * task-018 (Logic I) で `CompilerConfiguration` から [CaptureCodePluginConfig] を受け取り保持する。
 * Logic D (task-015) / file annotation (task-016) / line info (task-013) はこの config を読んで
 * 振る舞いを切り替える。本 ticket 時点では config は配線のみで、実消費は後続 ticket で行う
 * (`compat/IrInjector` への伝搬は task-015 が必要としたタイミングで追加する)。
 */
public class CaptureCodeIrExtension(
    private val config: CaptureCodePluginConfig = CaptureCodePluginConfig.DEFAULT,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        try {
            val injector = IrInjectorLoader.load(KotlinCompilerVersion.VERSION)
            // task-013 で IrInjector.transform は config を受け取るよう signature 拡張済。
            // compat-kXXXX が config.dedent / config.includeAnnotationLines を消費する。
            injector.transform(moduleFragment, pluginContext, config)
        } finally {
            // 同一 ClassLoader での連続 compile (kctfork) で前回コンパイルの marker / expression site が
            // 次回に漏れないよう、 両 registry をクリアする (task-008, task-017)。
            CaptureCodeMarkerRegistry.reset()
            CaptureCodeExpressionSiteRegistry.reset()
        }
    }
}
