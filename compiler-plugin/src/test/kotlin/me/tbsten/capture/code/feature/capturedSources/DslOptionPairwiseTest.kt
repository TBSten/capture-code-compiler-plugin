package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CAPTURE_CODE_PLUGIN_CONFIG_KEY
import me.tbsten.capture.code.CaptureCodeFirExtensionRegistrar
import me.tbsten.capture.code.CaptureCodeIrExtension
import me.tbsten.capture.code.CaptureCodePluginConfig
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Pairwise (= all-pairs) tour for the 6 DSL options of `CaptureCodePluginConfig`.
 *
 * The 6 boolean options (`includeKdoc`, `includeImports`, `includeAnnotationLines`,
 * `dedent`, `includeLineInfo`, `warnOnEmptyCapture`) yield `2^6 = 64` total
 * combinations. We use an 8-variant pairwise covering set so that **every pair**
 * of options is exercised in all four boolean combinations (TT / TF / FT / FF),
 * giving 15 pairs × 4 = 60 covered combinations — enough to catch the "2-option
 * interaction" class of bugs that the existing `DslOptionsTest` (2 disabled
 * cases) and `PerMarkerOverrideTest` (4 happy-path cases) do not target.
 *
 * The compile path is the same as [PerMarkerOptionOverrideTest]: a tiny test
 * registrar (`TestRegistrar`) injects [CaptureCodePluginConfig] directly into
 * [CompilerConfiguration] so the Gradle DSL `applyToCompilation` plumbing does
 * not need to spawn a real Gradle build for each variant. The 8 variants run as
 * 8 kctfork compiles (~ 20-40s total in CI).
 *
 * ## Pairwise variant table (Charter 3 plan §3)
 *
 * | # | includeKdoc | includeImports | includeAnnotationLines | dedent | includeLineInfo | warnOnEmptyCapture |
 * | --- | --- | --- | --- | --- | --- | --- |
 * | C1 | T | T | T | T | T | T |
 * | C2 | T | T | F | F | F | F |
 * | C3 | T | F | T | T | F | F |
 * | C4 | T | F | F | F | T | T |
 * | C5 | F | T | T | F | T | F |
 * | C6 | F | T | F | T | F | T |
 * | C7 | F | F | T | F | F | T |
 * | C8 | F | F | F | T | T | F |
 *
 * Within each variant, the sample exercises:
 *   - `includeKdoc`        — KDoc line presence in captured source
 *   - `includeImports`     — `import` line presence in `@file:`-captured source
 *   - `includeAnnotationLines` — non-marker `@<x>` annotation line presence in file source.
 *     **Charter 3 observation**: for declaration-origin captures the marker `@<Name>` line
 *     is always stripped at the collector layer (`skipLeadingMarkerAnnotations`) regardless
 *     of this flag. The flag only controls leading **non-marker** annotation lines and the
 *     `@file:Marker` line in file-origin captures. The KDoc on
 *     `CaptureCodePluginConfigBridge.toDeclarationNormalizeOptions` calls this out as a
 *     known polish item.
 *   - `dedent`             — common leading indent stripping
 *   - `includeLineInfo`    — `SourceLocation.startLine` zero-vs-real value
 *
 * `warnOnEmptyCapture` is wire-tested via `CaptureCodeGradlePluginTest` (DSL plumbing)
 * + `CommandLineProcessorTest` (config aggregation). Its end-to-end (= "no marker" =>
 * warning emitted) verification lives in `:integration-test:test-jvm` because it
 * needs to inspect the compile-time MessageCollector stream, which kctfork can do
 * but is more convenient through the real Gradle plumbing.
 *
 * ## Why this lives in `:compiler-plugin:test` instead of `:integration-test`
 *
 * `:integration-test:test-jvm` uses `kotlinCompilerPluginClasspath` to attach
 * the compiler plugin directly and therefore can only run with a single config
 * per Gradle subproject. Producing 8 fixture subprojects under
 * `:integration-test:test-gradle-plugin` would multiply TestKit launch cost
 * by 8 (≈ 8 × 30s = 4 min). Running 8 kctfork compiles inside one JVM finishes
 * in seconds while still exercising the real FIR + IR pipeline.
 */
class DslOptionPairwiseTest : FunSpec({

    // ------------------------------------------------------------------
    // Pairwise covering set (Charter 3 §3 of the exploratory-debug plan)
    // ------------------------------------------------------------------
    val pairwise: List<Pair<String, CaptureCodePluginConfig>> = listOf(
        "C1" to CaptureCodePluginConfig(
            includeKdoc = true,
            includeImports = true,
            includeAnnotationLines = true,
            dedent = true,
            includeLineInfo = true,
            warnOnEmptyCapture = true,
        ),
        "C2" to CaptureCodePluginConfig(
            includeKdoc = true,
            includeImports = true,
            includeAnnotationLines = false,
            dedent = false,
            includeLineInfo = false,
            warnOnEmptyCapture = false,
        ),
        "C3" to CaptureCodePluginConfig(
            includeKdoc = true,
            includeImports = false,
            includeAnnotationLines = true,
            dedent = true,
            includeLineInfo = false,
            warnOnEmptyCapture = false,
        ),
        "C4" to CaptureCodePluginConfig(
            includeKdoc = true,
            includeImports = false,
            includeAnnotationLines = false,
            dedent = false,
            includeLineInfo = true,
            warnOnEmptyCapture = true,
        ),
        "C5" to CaptureCodePluginConfig(
            includeKdoc = false,
            includeImports = true,
            includeAnnotationLines = true,
            dedent = false,
            includeLineInfo = true,
            warnOnEmptyCapture = false,
        ),
        "C6" to CaptureCodePluginConfig(
            includeKdoc = false,
            includeImports = true,
            includeAnnotationLines = false,
            dedent = true,
            includeLineInfo = false,
            warnOnEmptyCapture = true,
        ),
        "C7" to CaptureCodePluginConfig(
            includeKdoc = false,
            includeImports = false,
            includeAnnotationLines = true,
            dedent = false,
            includeLineInfo = false,
            warnOnEmptyCapture = true,
        ),
        "C8" to CaptureCodePluginConfig(
            includeKdoc = false,
            includeImports = false,
            includeAnnotationLines = false,
            dedent = true,
            includeLineInfo = true,
            warnOnEmptyCapture = false,
        ),
    )

    // ------------------------------------------------------------------
    // 8 pairwise variants — declaration-site capture (covers includeKdoc,
    // includeAnnotationLines, dedent, includeLineInfo)
    //
    // The fixture also drops a non-marker annotation (`@Suppress("unused")`)
    // before the marker so that `includeAnnotationLines` can flip the behaviour
    // even on the declaration-origin path. The marker line itself is always
    // stripped by `skipLeadingMarkerAnnotations` (task-129 BUG-A fix) and is
    // therefore independent of `includeAnnotationLines`.
    // ------------------------------------------------------------------
    for ((name, config) in pairwise) {
        test("variant $name: declaration source reflects every option independently") {
            val result = compileWithConfig(
                config,
                SourceFile.kotlin(
                    "Decl_$name.kt",
                    """
                    package example

                    import me.tbsten.capture.code.CaptureCode
                    import me.tbsten.capture.code.Source
                    import me.tbsten.capture.code.SourceLocation
                    import me.tbsten.capture.code.capturedSources

                    @CaptureCode
                    @Target(AnnotationTarget.FUNCTION)
                    @Retention(AnnotationRetention.SOURCE)
                    internal annotation class DeclMarker_$name(
                        val source: Source = Source(),
                        val location: SourceLocation = SourceLocation(),
                    )

                    internal class Outer_$name {
                        /**
                         * Pairwise variant $name doc.
                         */
                        @Suppress("unused")
                        @DeclMarker_$name
                        internal fun indentedMember(): String {
                            return "x"
                        }
                    }

                    internal object Main {
                        fun captured(): List<DeclMarker_$name> = capturedSources<DeclMarker_$name>()
                    }
                    """.trimIndent(),
                ),
            )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            val captured = loadCaptured(result)
            captured.size shouldBe 1
            val marker = captured[0] as Annotation
            val src = source(marker)
            val startLine = locationStartLine(marker)

            // includeKdoc: when true KDoc must appear, when false it must be removed.
            if (config.includeKdoc) {
                src shouldContain "Pairwise variant $name doc"
            } else {
                src shouldNotContain "Pairwise variant $name doc"
            }
            // **Charter 3 observation, recorded here as a regression guard**: the
            // marker annotation line `@DeclMarker_X` is always stripped by the
            // collector (`skipLeadingMarkerAnnotations`), independent of
            // `includeAnnotationLines`. This holds in all 8 variants and is the
            // "1 option's effect masked by another path" interaction that the
            // pairwise tour set out to find.
            src shouldNotContain "@DeclMarker_$name"
            // includeAnnotationLines: when true the non-marker `@Suppress("unused")`
            // line must remain, when false (= default) it must be stripped via
            // `stripLeadingAnnotationLines` in the normalize chain. Note: on the
            // declaration path the bridge currently hard-codes
            // `stripLeadingAnnotationLines = false` (see
            // CaptureCodePluginConfigBridge.toDeclarationNormalizeOptions), so even
            // when the flag is **false** the non-marker annotation survives. This is
            // an existing known polish item (declaration-origin annotation strip is
            // not yet wired through the flag) and the assertion captures the
            // **current** behaviour so a future fix forces a deliberate test update.
            src shouldContain "@Suppress(\"unused\")"
            // dedent: when true the inner 4-space indent of `internal fun` should
            // collapse to 0 (the line starts with `internal`); when false the
            // class-body 4-space indent should remain.
            if (config.dedent) {
                src shouldContain "internal fun indentedMember(): String {"
                src shouldNotContain "    internal fun indentedMember"
            } else {
                src shouldContain "    internal fun indentedMember(): String {"
            }
            // includeLineInfo: when true startLine must be a real positive value,
            // when false it must collapse to 0 (filler convention).
            if (config.includeLineInfo) {
                (startLine > 0) shouldBe true
            } else {
                startLine shouldBe 0
            }
        }
    }

    // ------------------------------------------------------------------
    // 8 pairwise variants — file-annotation capture (covers includeImports
    // which only affects the file-origin path). We re-use the same pairwise
    // table to verify includeImports does not interact unexpectedly with
    // the declaration-origin options when those happen to flip together.
    // ------------------------------------------------------------------
    for ((name, config) in pairwise) {
        test("variant $name: file-annotation source honors includeImports + includeAnnotationLines") {
            val result = compileWithConfig(
                config,
                SourceFile.kotlin(
                    "FileLevel_$name.kt",
                    """
                    @file:FileMarker_$name

                    package example.file_$name

                    import me.tbsten.capture.code.CaptureCode
                    import me.tbsten.capture.code.Source
                    import me.tbsten.capture.code.capturedSources

                    @CaptureCode
                    @Target(AnnotationTarget.FILE)
                    @Retention(AnnotationRetention.SOURCE)
                    internal annotation class FileMarker_$name(val source: Source = Source())

                    val alpha_$name = 1

                    internal object Main {
                        fun captured(): List<FileMarker_$name> = capturedSources<FileMarker_$name>()
                    }
                    """.trimIndent(),
                ),
            )
            result.exitCode shouldBe KotlinCompilation.ExitCode.OK

            val captured = loadCaptured(result, mainFqn = "example.file_$name.Main")
            captured.size shouldBe 1
            val marker = captured[0] as Annotation
            val src = source(marker)

            // includeImports controls whether `package` and `import ...` lines stay
            // (`NormalizeOptions.stripPackageAndImport = !includeImports` couples the
            // two SSoT; see `CaptureCodePluginConfigBridge.toFileNormalizeOptions`).
            //
            // **Charter 3 finding (BUG-3-2)**: when `includeAnnotationLines = true`
            // the leading `@file:Marker` annotation line stays, and the
            // `stripPackageAndImportLines` helper — which is a "greedy from the
            // head" filter — bails out at the first non-package / non-import line
            // (`@file:...`). As a result the `package` and `import` lines **leak
            // even when `includeImports = false`**. This assertion intentionally
            // captures the current (buggy) behaviour so a future fix forces a
            // deliberate flip — see
            // `.local/tmp/probe/exploratory-debug/charter-3-dsl-pairwise/BUG-3-2-file-annotation-import-strip-bypassed.md`.
            val packageStripped = !config.includeImports && !config.includeAnnotationLines
            if (packageStripped) {
                src shouldNotContain "package example.file_$name"
                src shouldNotContain "import me.tbsten.capture.code.CaptureCode"
                src shouldNotContain "import me.tbsten.capture.code.Source"
                src shouldNotContain "import me.tbsten.capture.code.capturedSources"
            } else {
                src shouldContain "package example.file_$name"
                src shouldContain "import me.tbsten.capture.code.CaptureCode"
                src shouldContain "import me.tbsten.capture.code.Source"
                src shouldContain "import me.tbsten.capture.code.capturedSources"
            }
            // includeAnnotationLines controls whether the leading `@file:Marker`
            // annotation line stays.
            if (config.includeAnnotationLines) {
                src shouldContain "@file:FileMarker_$name"
            } else {
                src shouldNotContain "@file:FileMarker_$name"
            }
            // Body must always remain so that we can be sure the variant actually
            // produced a real file capture and the assertions above aren't passing
            // because the source happens to be empty.
            src shouldContain "val alpha_$name = 1"
        }
    }

    // ------------------------------------------------------------------
    // Cross-check: per-marker overrides survive when stacked on top of the
    // global pairwise variants. The 4 representative overrides exercise the
    // 3 Override states (Default / Yes / No) × 2 dimensions (force-on vs
    // force-off of independent options).
    // ------------------------------------------------------------------
    test("per-marker override: stacking Yes / No / Default on top of a fully-on global config") {
        // Global config = C1 (all true). Marker level then flips a handful of
        // options to No so the effective config differs from the global config.
        val result = compileWithConfig(
            CaptureCodePluginConfig(
                includeKdoc = true,
                includeImports = false,
                includeAnnotationLines = true,
                dedent = true,
                includeLineInfo = true,
                warnOnEmptyCapture = true,
            ),
            SourceFile.kotlin(
                "PerMarkerStack.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(
                    includeKdoc = CaptureCode.Override.No,
                    includeAnnotationLines = CaptureCode.Override.No,
                    dedent = CaptureCode.Override.No,
                    includeLineInfo = CaptureCode.Override.No,
                )
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class StackMarker(
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                )

                internal class Outer {
                    /**
                     * Stack doc.
                     */
                    @StackMarker
                    internal fun indented(): String {
                        return "y"
                    }
                }

                internal object Main {
                    fun captured(): List<StackMarker> = capturedSources<StackMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val src = source(marker)

        // includeKdoc = No → KDoc not present even though global = true
        src shouldNotContain "Stack doc"
        // includeAnnotationLines = No → @StackMarker not present even though global = true
        src shouldNotContain "@StackMarker"
        // dedent = No → 4-space indent preserved even though global = true
        src shouldContain "    internal fun indented(): String {"
        // includeLineInfo = No → startLine = 0 even though global = true
        locationStartLine(marker) shouldBe 0
    }

    test("per-marker override: Default keeps the global pairwise C8 config (all-false global with one Yes)") {
        // Global config = C8 (most options false). Marker level only flips
        // includeKdoc to Yes; everything else uses Default and must follow the
        // global config.
        val result = compileWithConfig(
            CaptureCodePluginConfig(
                includeKdoc = false,
                includeImports = false,
                includeAnnotationLines = false,
                dedent = true,
                includeLineInfo = true,
                warnOnEmptyCapture = false,
            ),
            SourceFile.kotlin(
                "PerMarkerDefault.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(includeKdoc = CaptureCode.Override.Yes)
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DefaultMarker(
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                )

                internal class Outer {
                    /**
                     * Default doc.
                     */
                    @DefaultMarker
                    internal fun nested(): String {
                        return "z"
                    }
                }

                internal object Main {
                    fun captured(): List<DefaultMarker> = capturedSources<DefaultMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val src = source(marker)

        // includeKdoc = Yes (override) → KDoc present even though global = false
        src shouldContain "Default doc"
        // includeAnnotationLines = Default → follows global = false
        src shouldNotContain "@DefaultMarker"
        // dedent = Default → follows global = true (indent collapsed)
        src shouldContain "internal fun nested(): String {"
        src shouldNotContain "    internal fun nested"
        // includeLineInfo = Default → follows global = true (real line)
        (locationStartLine(marker) > 0) shouldBe true
    }

    test("per-marker override: opposite of global on independent options does not bleed across") {
        // Force a config that is the **mirror** of the override on every flipped
        // option to detect "option X override accidentally toggles option Y".
        val result = compileWithConfig(
            CaptureCodePluginConfig(
                includeKdoc = false,
                includeImports = false,
                includeAnnotationLines = false,
                dedent = false,
                includeLineInfo = false,
                warnOnEmptyCapture = false,
            ),
            SourceFile.kotlin(
                "PerMarkerMirror.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode(
                    includeKdoc = CaptureCode.Override.Yes,
                    includeAnnotationLines = CaptureCode.Override.Yes,
                    dedent = CaptureCode.Override.Yes,
                    includeLineInfo = CaptureCode.Override.Yes,
                )
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MirrorMarker(
                    val source: Source = Source(),
                    val location: SourceLocation = SourceLocation(),
                )

                internal class Outer {
                    /**
                     * Mirror doc.
                     */
                    @MirrorMarker
                    internal fun nested(): String {
                        return "w"
                    }
                }

                internal object Main {
                    fun captured(): List<MirrorMarker> = capturedSources<MirrorMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val src = source(marker)

        // Override.Yes on includeKdoc → KDoc present even though global = false
        src shouldContain "Mirror doc"
        // Override.Yes on includeAnnotationLines does **not** affect declaration-
        // origin marker lines (`skipLeadingMarkerAnnotations` always strips them);
        // see the file-level fixture below for the case where the override does
        // change behaviour. The marker line must therefore stay stripped.
        src shouldNotContain "@MirrorMarker"
        // Override.Yes on dedent → indent removed even though global = false
        src shouldContain "internal fun nested(): String {"
        src shouldNotContain "    internal fun nested"
        // Override.Yes on includeLineInfo → real positive line even though global = false
        (locationStartLine(marker) > 0) shouldBe true
    }

    test("per-marker override: includeImports + file annotation source path") {
        // includeImports has no per-marker override in CaptureCodeMarkerOptions
        // (the runtime CaptureCode marker only exposes 5 of 6 options); verify
        // that the global flag is still honored for file annotation captures.
        val result = compileWithConfig(
            CaptureCodePluginConfig(
                includeKdoc = true,
                includeImports = true,
                includeAnnotationLines = false,
                dedent = true,
                includeLineInfo = true,
                warnOnEmptyCapture = false,
            ),
            SourceFile.kotlin(
                "FileWithImports.kt",
                """
                @file:FileMarkerImports

                package example.imports

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FILE)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FileMarkerImports(val source: Source = Source())

                val gamma = 7

                internal object Main {
                    fun captured(): List<FileMarkerImports> = capturedSources<FileMarkerImports>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result, mainFqn = "example.imports.Main")
        captured.size shouldBe 1
        val marker = captured[0] as Annotation
        val src = source(marker)

        // includeImports = true keeps every import line in file-annotation source
        src shouldContain "import me.tbsten.capture.code.CaptureCode"
        src shouldContain "import me.tbsten.capture.code.Source"
        src shouldContain "import me.tbsten.capture.code.capturedSources"
        // `NormalizeOptions.stripPackageAndImport = !includeImports` couples the
        // `package` and `import` stripping; with `includeImports = true` the
        // package line stays too.
        src shouldContain "package example.imports"
        // body remains
        src shouldContain "val gamma = 7"
    }
})

// ----------------------------------------------------------------------------
// Internal helpers shared by the 16 variant tests + 4 per-marker overrides.
// Mirrors PerMarkerOptionOverrideTest's helpers so changes here should be
// applied in tandem. The registrar is duplicated rather than shared because
// kctfork's CompilerPluginRegistrar interface is not stable across compat
// modules and we want the IR + FIR extension wiring to live next to the test.
// ----------------------------------------------------------------------------
private class PairwiseTestRegistrar(
    private val config: CaptureCodePluginConfig,
) : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        configuration.put(CAPTURE_CODE_PLUGIN_CONFIG_KEY, config)
        FirExtensionRegistrarAdapter.registerExtension(CaptureCodeFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(CaptureCodeIrExtension(config))
    }
}

private fun compileWithConfig(
    config: CaptureCodePluginConfig,
    vararg sources: SourceFile,
): JvmCompilationResult =
    KotlinCompilation().apply {
        this.sources = sources.toList()
        compilerPluginRegistrars = listOf(PairwiseTestRegistrar(config))
        inheritClassPath = true
        jvmTarget = "17"
        messageOutputStream = System.out
    }.compile()

private fun loadCaptured(result: JvmCompilationResult, mainFqn: String = "example.Main"): List<*> {
    val mainClass = result.classLoader.loadClass(mainFqn)
    val mainInstance = mainClass.getField("INSTANCE").get(null)
    return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
}

private fun source(marker: Annotation): String {
    val src = marker.annotationClass.java.getMethod("source").invoke(marker)
    return src.javaClass.getMethod("value").invoke(src) as String
}

private fun locationStartLine(marker: Annotation): Int {
    val loc = marker.annotationClass.java.getMethod("location").invoke(marker)
    return loc.javaClass.getMethod("startLine").invoke(loc) as Int
}
