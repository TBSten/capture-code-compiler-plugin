package me.tbsten.capture.code.compat

import me.tbsten.capture.code.CaptureCodePluginConfig

/**
 * Process-scoped holder for the **most recently registered**
 * [CaptureCodePluginConfig].
 *
 * Set during plugin registration (`CaptureCodeCompilerPluginRegistrar.registerExtensions`)
 * and read by FIR checkers that need the global plugin config to detect
 * redundant per-marker overrides (Logic F warning side, task-123).
 *
 * ## Why a static holder
 *
 * The IR side already receives [CaptureCodePluginConfig] through
 * `CaptureCodeIrExtension`'s constructor, but FIR
 * `FirAdditionalCheckersExtension` factories registered through
 * `CompatContext.firAdditionalCheckersExtensions()` have no formal channel
 * to receive the config. Threading the config through the `CompatContext`
 * SPI would require a 6-compat-module surface change (every
 * `firAdditionalCheckersExtensions()` signature). For the single FIR warning
 * that needs the config (task-123 `WarnIfOverrideNoEffect`) a static holder
 * is the lighter alternative — mirrors [CaptureCodeCompatHolder]'s
 * process-scoped lazy pattern.
 *
 * ## Lifecycle
 *
 * Each compile session writes via [set] from
 * `CaptureCodeCompilerPluginRegistrar.registerExtensions`. The Gradle Kotlin
 * daemon reuses the same JVM across compiles, so a stale value from a prior
 * compile would persist between sessions. [set] is therefore called
 * **unconditionally on every plugin-registration call**, which is also the
 * exact moment when the new `CompilerConfiguration` is materialized; the
 * holder is always in sync with the running compile.
 *
 * The holder defaults to [CaptureCodePluginConfig.DEFAULT] until [set] is
 * called, so unit tests that never go through `CaptureCodeCompilerPluginRegistrar`
 * still see a usable config.
 *
 * ## Concurrent compile sessions (caveat)
 *
 * Same-JVM concurrent compile sessions are **not** supported: the most recent
 * [set] wins for the entire process. Gradle's Kotlin daemon serialises compile
 * requests per worker so this is acceptable in the normal build path. IDE
 * plugin hosts (IntelliJ etc.) that run multiple `FirAdditionalCheckersExtension`
 * analyses in parallel inside one JVM could observe a stale config if a second
 * `set` lands between another session's plugin registration and its FIR
 * checkers reading via [get]. We accept this trade-off because the alternative
 * (threading config through the `CompatContext` SPI) would expand the surface
 * across 6 compat-kXXX modules for a single FIR-side consumer.
 *
 * task-123: introduced for `WarnIfOverrideNoEffect` wire-up.
 */
public object CaptureCodePluginConfigHolder {

    @Volatile
    private var current: CaptureCodePluginConfig = CaptureCodePluginConfig.DEFAULT

    /** Updates the holder with the [config] resolved for the current compile. */
    public fun set(config: CaptureCodePluginConfig) {
        current = config
    }

    /** Returns the most recently registered config, or [CaptureCodePluginConfig.DEFAULT]. */
    public fun get(): CaptureCodePluginConfig = current
}
