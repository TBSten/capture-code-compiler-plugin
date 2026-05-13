package me.tbsten.capture.code.spike

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * task-009 spike 専用の compiler plugin registrar。
 *
 * production の [me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar] には触らず、
 * 観察用の FIR / IR extension のみを登録する。
 *
 * kctfork test の `compilerPluginRegistrars = listOf(SpikePluginRegistrar(report, markerFqns))` 形で利用する。
 * （ただし [SpikeFirCheckersExtension] は no-arg constructor を要求するため、観察対象の [SpikeReport] は
 *   [SpikeReportHolder] 経由で受け渡す。test driver が compile 前後で holder を setup する。）
 */
internal class SpikePluginRegistrar(
    private val report: SpikeReport,
    private val markerFqns: Set<String>,
) : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // compile 前後で holder をセット/解除する責務は test driver にある。
        // ここでは念のため毎回 set (同一 process で複数 compile が走る kctfork の場合のため)
        SpikeReportHolder.current = report
        SpikeReportHolder.markerFqns = markerFqns

        FirExtensionRegistrarAdapter.registerExtension(SpikeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(SpikeIrExtension(report, markerFqns))
    }
}
