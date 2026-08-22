package me.tbsten.capture.code.compat

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
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

    /**
     * Emits a [CompilerMessageSeverity.LOGGING]-level diagnostic via the currently
     * registered collector. Intended for plugin-internal debug breadcrumbs (e.g.
     * source-extraction skip notes) that should remain invisible during normal
     * builds but surface under `--info` / verbose Gradle logging.
     *
     * task-137: introduced to debug-log silent source-extraction `return null` /
     * `continue` paths without bothering users.
     */
    public fun reportLogging(message: String) {
        if (current === MessageCollector.NONE) return
        current.report(CompilerMessageSeverity.LOGGING, message, null)
    }

    /**
     * Emits a [CompilerMessageSeverity.ERROR]-level diagnostic via the currently
     * registered collector, failing the compilation.
     *
     * bug-008: FIR checker 側で新しい diagnostic を追加する場合、 本来は
     * `KtDiagnosticFactory*` を使うが、 factory の宣言 + renderer MAP 登録は各
     * `compat-kXXX/CompatContextImpl.kt` の nested diagnostics object に閉じており
     * (`CompatContext.diagnosticFactory(id)` は未登録 id に `null` を返す)、 新 factory の
     * 追加は 6 つの compat module すべてに波及する。 compat module を変更せずに error を
     * 出す経路として、 IR phase の error 報告
     * ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall.RewriteCapturedSourceCall]
     * の `MessageCollector.report(ERROR, ...)`) と同じ機構を FIR phase にも開放する。
     *
     * ERROR severity の report は `GroupingMessageCollector.hasErrors()` を true にするため、
     * CLI compile は `COMPILATION_ERROR` で終了する (= compile は失敗する)。 collector が
     * [MessageCollector.NONE] の場合 (registrar を通らない unit test 等) は silent no-op に
     * degrade する。
     *
     * @param message 表示する error 文面 (feature ローカル `*Errors.kt` の SSoT を
     *   `MessageFormat.format` で展開したもの)
     * @param location error の位置。 `null` なら位置情報なしで報告
     */
    public fun reportError(message: String, location: CompilerMessageLocation? = null) {
        current.report(CompilerMessageSeverity.ERROR, message, location)
    }
}
