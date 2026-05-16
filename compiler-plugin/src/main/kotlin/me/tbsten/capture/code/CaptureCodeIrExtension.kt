package me.tbsten.capture.code

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Capture Code compiler plugin の IR 拡張点。
 *
 * 現在の Kotlin バージョンに合った [me.tbsten.capture.code.compat.CompatContext] を
 * ServiceLoader 経由で選択し、 その `transformIr` メソッドに委譲する。
 *
 * Logic A の動的検出結果は [CaptureCodeMarkerRegistry] (FIR phase の checker が蓄積) を
 * 通じて受け渡されるため、本 extension では FIR session に直接アクセスする必要はない。
 *
 * ## Registry lifecycle
 *
 * [CaptureCodeMarkerRegistry] は plugin module の object として表現された compilation-scoped holder。
 * 同一 JVM (= 同一 ClassLoader) で複数コンパイル (例: kctfork による連続 test compile) が走る場合、
 * 前回の marker FqN が次回に漏れないよう、本 extension の `generate` 完了時に [reset]
 * (try/finally) してクリアする。
 *
 * KMP の複数 target (例: jvmMain + jsMain) のハンドリングは将来の拡張ポイント。
 *
 * ## Plugin config
 *
 * Logic I で `CompilerConfiguration` から [CaptureCodePluginConfig] を受け取り保持する。
 * Logic D (dedent) / file annotation / line info はこの config を読んで振る舞いを切り替える。
 */
public class CaptureCodeIrExtension(
    private val config: CaptureCodePluginConfig = CaptureCodePluginConfig.DEFAULT,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        try {
            // Metro pattern: IrInjector + FirAnalyzer の 2 interface を CompatContext 1 つに
            // 統合している。 CaptureCodeCompatHolder.context は process-scoped lazy で
            // ServiceLoader を 1 回だけ走らせ、 K2 compiler 起動の hot path で全 generate()
            // invocation 共有する。
            CaptureCodeCompatHolder.context.transformIr(moduleFragment, pluginContext, config)
        } finally {
            // 同一 ClassLoader での連続 compile (kctfork) で前回コンパイルの marker / expression site が
            // 次回に漏れないよう、 marker registry と expression site registry の両方をクリアする。
            CaptureCodeMarkerRegistry.reset()
            CaptureCodeExpressionSiteRegistry.reset()
        }
    }
}
