package me.tbsten.capture.code.compat

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Process-scoped holder for the IR-phase [MessageCollector] resolved at
 * plugin-registration time.
 *
 * ## Why a static holder
 *
 * Mirror of [CaptureCodePluginConfigHolder]: the
 * [me.tbsten.capture.code.CaptureCodeIrExtension] needs a `MessageCollector`
 * to fire IR-phase warnings (task-120-B Phase 7: `WarnIfNoMarkerFound`), but
 * `IrGenerationExtension.generate(...)` only receives `IrModuleFragment` +
 * `IrPluginContext` and the latter exposes `getMessageCollector()` **only on
 * K2.4-RC+**. On the K2.0 baseline that main module is compiled against the
 * accessor does not exist, so threading the collector through `IrPluginContext`
 * is not safely portable.
 *
 * Instead, the plugin registrar
 * (`CaptureCodeCompilerPluginRegistrar.registerExtensions`) extracts the
 * collector from the freshly-materialised
 * [org.jetbrains.kotlin.config.CompilerConfiguration] via
 * [CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY] and publishes it here. The
 * `CompilerConfiguration` key is stable across all supported baselines
 * (K2.0 .. K2.4-RC verified) so no compat SPI is needed.
 *
 * ## Lifecycle
 *
 * - [set] is called unconditionally at every plugin registration
 *   (`CaptureCodeCompilerPluginRegistrar.registerExtensions`).
 * - [get] is called from `CaptureCodeIrExtension.generate(...)` at the start of
 *   each IR pass.
 *
 * Same concurrency caveat as [CaptureCodePluginConfigHolder]: parallel compile
 * sessions in the same JVM (IDE hosts) can race; Gradle's Kotlin daemon
 * serialises requests per worker so the normal build path is safe.
 *
 * Defaults to [MessageCollector.NONE] before any [set] call so unit tests that
 * never go through the registrar still see a usable (silent) collector.
 *
 * task-120-B Phase 7: introduced for `WarnIfNoMarkerFound` wire-up.
 */
public object CaptureCodeMessageCollectorHolder {

    @Volatile
    private var current: MessageCollector = MessageCollector.NONE

    /** Updates the holder with the [collector] resolved for the current compile. */
    public fun set(collector: MessageCollector) {
        current = collector
    }

    /** Returns the most recently registered collector, or [MessageCollector.NONE]. */
    public fun get(): MessageCollector = current

    /**
     * Convenience: extract the [MessageCollector] from a [configuration] (falling
     * back to [MessageCollector.NONE] if the key is not present) and publish it
     * via [set]. Called from `CaptureCodeCompilerPluginRegistrar.registerExtensions`.
     */
    public fun setFrom(configuration: CompilerConfiguration) {
        val collector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)
            ?: MessageCollector.NONE
        set(collector)
    }
}
