package me.tbsten.capture.code.feature.markerDefinition

import me.tbsten.capture.code.CaptureCodePluginConfig

/**
 * Per-marker option overrides extracted from `@CaptureCode(...)` annotation
 * arguments (Logic A).
 *
 * Each field mirrors a corresponding [CaptureCodePluginConfig] flag and uses
 * the tri-state [Override] enum so the "did the user set anything?" question
 * stays distinguishable from "the user set `true`" / "the user set `false`".
 *
 * SSOT note: the public [me.tbsten.capture.code.CaptureCode.Override] enum
 * (defined in `:annotation`) and this internal-but-public copy carry the same
 * three values (`Default` / `Yes` / `No`). FIR side reads the annotation
 * arguments and projects them onto this internal representation so the
 * `:compat` module does not need to depend on `:annotation` at compile time
 * — both modules independently re-state the small enum.
 *
 * @property includeKdoc override for [CaptureCodePluginConfig.includeKdoc]
 * @property includeImports override for [CaptureCodePluginConfig.includeImports]
 * @property includeAnnotationLines override for [CaptureCodePluginConfig.includeAnnotationLines]
 * @property dedent override for [CaptureCodePluginConfig.dedent]
 * @property includeLineInfo override for [CaptureCodePluginConfig.includeLineInfo]
 */
public data class CaptureCodeMarkerOptions(
    val includeKdoc: Override = Override.Default,
    val includeImports: Override = Override.Default,
    val includeAnnotationLines: Override = Override.Default,
    val dedent: Override = Override.Default,
    val includeLineInfo: Override = Override.Default,
) {
    /**
     * Tri-state override flag for a single option.
     *
     * Mirrors `me.tbsten.capture.code.CaptureCode.Override` (defined in
     * `:annotation`), but lives here so `:compat` stays free of an
     * `:annotation` compile-time dependency.
     */
    public enum class Override {
        /** Use the Gradle plugin config (or the library built-in default). */
        Default,

        /** Force the option to `true` for this marker. */
        Yes,

        /** Force the option to `false` for this marker. */
        No,
    }

    public companion object {
        /** All options at [Override.Default] — equivalent to "no per-marker override". */
        public val DEFAULT: CaptureCodeMarkerOptions = CaptureCodeMarkerOptions()
    }
}

/**
 * Returns a [CaptureCodePluginConfig] effective for a single capture site,
 * applying the per-marker overrides from [markerOptions] on top of the global
 * Gradle DSL config (`this`).
 *
 * Resolution rule per option:
 *
 * - `Override.Yes` → `true`
 * - `Override.No` → `false`
 * - `Override.Default` → keep the global config value
 *
 * The returned config is a fresh [CaptureCodePluginConfig] instance. If the
 * marker has no overrides at all ([CaptureCodeMarkerOptions.DEFAULT]), the
 * returned config is **structurally equal** to `this` (and the same instance
 * is returned for the cheap fast-path).
 */
public fun CaptureCodePluginConfig.effectiveFor(
    markerOptions: CaptureCodeMarkerOptions,
): CaptureCodePluginConfig {
    if (markerOptions == CaptureCodeMarkerOptions.DEFAULT) return this
    return CaptureCodePluginConfig(
        includeKdoc = resolve(markerOptions.includeKdoc, includeKdoc),
        includeImports = resolve(markerOptions.includeImports, includeImports),
        includeAnnotationLines = resolve(markerOptions.includeAnnotationLines, includeAnnotationLines),
        dedent = resolve(markerOptions.dedent, dedent),
        includeLineInfo = resolve(markerOptions.includeLineInfo, includeLineInfo),
    )
}

private fun resolve(
    override: CaptureCodeMarkerOptions.Override,
    configured: Boolean,
): Boolean = when (override) {
    CaptureCodeMarkerOptions.Override.Yes -> true
    CaptureCodeMarkerOptions.Override.No -> false
    CaptureCodeMarkerOptions.Override.Default -> configured
}

/**
 * Returns the names of options that the marker explicitly overrides to the
 * **same value** the global config already carries. Such overrides have no
 * effect at runtime and are good candidates for the
 * `CC_MARKER_OVERRIDE_NO_EFFECT` warning (Logic F, task-123).
 *
 * - `Override.Default` → never reported (not an override).
 * - `Override.Yes` / `Override.No` → reported iff the resulting effective
 *   value equals the global config field on [globalConfig].
 *
 * Order of the returned list matches the option declaration order on
 * [CaptureCodeMarkerOptions].
 */
public fun CaptureCodeMarkerOptions.diffFromDefault(
    globalConfig: CaptureCodePluginConfig,
): List<String> = buildList {
    fun check(
        name: String,
        override: CaptureCodeMarkerOptions.Override,
        configured: Boolean,
    ) {
        if (override == CaptureCodeMarkerOptions.Override.Default) return
        val overrideValue = override == CaptureCodeMarkerOptions.Override.Yes
        if (overrideValue == configured) add(name)
    }
    check("includeKdoc", includeKdoc, globalConfig.includeKdoc)
    check("includeImports", includeImports, globalConfig.includeImports)
    check("includeAnnotationLines", includeAnnotationLines, globalConfig.includeAnnotationLines)
    check("dedent", dedent, globalConfig.dedent)
    check("includeLineInfo", includeLineInfo, globalConfig.includeLineInfo)
}
