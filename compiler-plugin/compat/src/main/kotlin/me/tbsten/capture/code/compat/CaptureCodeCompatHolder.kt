package me.tbsten.capture.code.compat

import org.jetbrains.kotlin.config.KotlinCompilerVersion

/**
 * Process-scoped lazy holder for the resolved [CompatContext].
 *
 * `CompatContext.load(...)` walks a `ServiceLoader` and parses every Factory's
 * `minVersion`. Doing that on every FIR checker invocation would be wasteful, so
 * we cache the result here for the lifetime of the JVM (same `ClassLoader`).
 *
 * The holder is shared across the IR extension (`CaptureCodeIrExtension`) and
 * every FIR checker / collector that needs to consume drift-prone APIs
 * (`CaptureCodeFirExpressionSiteCollector`, `CapturedSourcesCallChecker`,
 * `MarkerAnnotationChecker`). Centralising the lookup keeps the Kotlin version
 * detection in a single place — the SSOT here is
 * [KotlinCompilerVersion.VERSION] (= the version of `kotlin-compiler-embeddable`
 * loaded into the plugin's `ClassLoader`).
 */
public object CaptureCodeCompatHolder {

    /**
     * Lazily-resolved [CompatContext] for the current Kotlin compiler version.
     *
     * Resolution is performed once per `ClassLoader`; subsequent reads return
     * the cached instance.
     */
    public val context: CompatContext by lazy {
        CompatContext.load(KotlinCompilerVersion.VERSION)
    }
}
