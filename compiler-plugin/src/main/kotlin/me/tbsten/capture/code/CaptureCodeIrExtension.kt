package me.tbsten.capture.code

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
 */
public class CaptureCodeIrExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        try {
            val injector = IrInjectorLoader.load(KotlinCompilerVersion.VERSION)
            injector.transform(moduleFragment, pluginContext)
        } finally {
            CaptureCodeMarkerRegistry.reset()
        }
    }
}
