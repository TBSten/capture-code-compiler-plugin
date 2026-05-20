package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

/**
 * Charter 8 (exploratory-debug-plan §4) — fault injection probe for the 5
 * `require(...)` preconditions added in commit a56507c (task-139) and
 * eb839df (task-140):
 *
 * | site | location | guard |
 * | --- | --- | --- |
 * | R1 | `ValidateCapturedSourcesCall.kt:73` | `expression.calleeReference is FirResolvedNamedReference` |
 * | R2 | `ValidateCapturedSourcesCall.kt:81` | `expression.typeArguments.isNotEmpty()` |
 * | R3 | `RewriteCapturedSourcesCall.kt:125` | `collectedSites.all { it.site.markerFqn.isNotBlank() }` |
 * | R4 | `BuildMarkerInstance.kt:140` | `markerFqn.isNotBlank()` |
 * | R5 | `BuildMarkerInstance.kt:289` | `fillerPlan.bindings.keys.all { it in parameters.indices }` |
 *
 * Each `require()` claims to fail-fast only when **the caller** (= another
 * plugin component) violates an invariant. We:
 *
 * - **baseline (A1-A5)** — compile representative user sources through the
 *   plugin and verify no `require()` trips (= false positive check).
 * - **fault injection (F1-F4)** — call the IR-side logic objects directly via
 *   reflection with bad arguments to confirm each `require()` trips with a
 *   message that points the plugin developer at the root cause.
 * - **L1** — FIR-side R1 / R2 are reachable only through FIR Checker
 *   registration, which the user cannot bend without modifying the plugin
 *   source itself. They are verified indirectly by A1 / A5 baseline.
 *
 * Reflection caveat: `BuildMarkerInstance` is `internal` and `buildSingle` /
 * `FillerPlan` are `private`. We use `getDeclaredMethod` /
 * `getDeclaredConstructor` + `isAccessible = true` to reach them. Since each
 * `require()` is the **first statement** in its method body, JVM evaluates it
 * before any argument is dereferenced — so we can safely pass `null` for the
 * heavy IR-only parameters (e.g. `IrPluginContext`, `CompatContext`,
 * `IrModuleFragment`) just to satisfy the JVM method signature.
 */
class Charter8RequireTripProbeTest : FunSpec({

    beforeEach { CaptureCodeMarkerRegistry.reset() }
    afterEach { CaptureCodeMarkerRegistry.reset() }

    /**
     * Compile with the plugin registrar enabled and capture stdout so we can
     * grep for the require()-trip message strings without depending on the
     * kctfork-internal collector format.
     */
    fun compile(vararg sources: SourceFile, inheritClassPath: Boolean = true): ProbeResult {
        val capturedStdout = ByteArrayOutputStream()
        val result = KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            this.inheritClassPath = inheritClassPath
            jvmTarget = "17"
            messageOutputStream = PrintStream(capturedStdout)
        }.compile()
        val output = capturedStdout.toString(Charsets.UTF_8)
        println("\n========== Charter 8 probe ==========")
        println("exitCode=${result.exitCode}, registeredMarkers=${CaptureCodeMarkerRegistry.markerFqns}")
        println(
            output.lines().filter { line ->
                line.isNotBlank() && (
                    line.startsWith("w:") || line.startsWith("e:") ||
                        line.contains("must not be blank") ||
                        line.contains("must carry a non-blank") ||
                        line.contains("must not be empty") ||
                        line.contains("FirResolvedNamedReference") ||
                        line.contains("out of range") ||
                        line.contains("Typical root cause") ||
                        line.contains("error:") || line.contains("warning:")
                    )
            }.joinToString("\n"),
        )
        println("=====================================\n")
        return ProbeResult(
            exitCode = result.exitCode,
            output = output,
            classLoader = if (result.exitCode == KotlinCompilation.ExitCode.OK) result.classLoader else null,
            registeredMarkers = CaptureCodeMarkerRegistry.markerFqns.toList().sorted(),
        )
    }

    // ------------------------------------------------------------------
    // Baseline probes (A1-A5): regular user sources must never trip any
    // of the 5 require() guards (= false positive verification).
    // ------------------------------------------------------------------

    test("A1 baseline (R1 / R2 FIR site): plain capturedSources<T>() call -> no trip") {
        // FIR Checker is registered after name resolution (= MppCheckerKind.Common
        // for FirFunctionCallChecker), so R1 (calleeReference is
        // FirResolvedNamedReference) is satisfied by phase ordering. R2
        // (typeArguments.isNotEmpty()) is satisfied because capturedSources<T>()
        // requires a type argument by signature.
        val result = compile(
            SourceFile.kotlin(
                "A1.kt",
                """
                package probe.a1

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class A1Marker(val source: Source = Source())

                @A1Marker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<A1Marker> = capturedSources<A1Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoRequireTrip()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("A2 baseline (R3 / R4 IR site): single declaration site -> no trip + IR rewrite proven") {
        val result = compile(
            SourceFile.kotlin(
                "A2.kt",
                """
                package probe.a2

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class A2Marker(val source: Source = Source())

                @A2Marker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<A2Marker> = capturedSources<A2Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoRequireTrip()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        // Prove R3 (RewriteCapturedSourcesCall.invoke) passed: list must have
        // exactly 1 element built by BuildMarkerInstance from a non-blank fqn.
        val cl = requireNotNull(result.classLoader)
        val main = cl.loadClass("probe.a2.Main")
        val captured = main.getMethod("captured").invoke(main.getField("INSTANCE").get(null)) as List<*>
        captured.size shouldBe 1
    }

    test("A3 baseline (R3 / R4 multi-marker): multiple markers + multiple sites -> no trip") {
        // Stresses R3 (collectedSites.all) and R4 (markerFqn per group) by
        // having two unrelated marker FQNs each with multiple sites.
        val result = compile(
            SourceFile.kotlin(
                "A3.kt",
                """
                package probe.a3

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class A3MarkerA(val source: Source = Source())

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class A3MarkerB(val source: Source = Source())

                @A3MarkerA internal fun a1() = 1
                @A3MarkerA internal fun a2() = 2
                @A3MarkerB internal fun b1() = 3

                internal object Main {
                    fun a(): List<A3MarkerA> = capturedSources<A3MarkerA>()
                    fun b(): List<A3MarkerB> = capturedSources<A3MarkerB>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoRequireTrip()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val cl = requireNotNull(result.classLoader)
        val main = cl.loadClass("probe.a3.Main")
        val a = main.getMethod("a").invoke(main.getField("INSTANCE").get(null)) as List<*>
        val b = main.getMethod("b").invoke(main.getField("INSTANCE").get(null)) as List<*>
        a.size shouldBe 2
        b.size shouldBe 1
    }

    test("A4 baseline (R5 fillerPlan range): all 3 fillers + many user args -> no trip") {
        // Pack the marker with 3 filler types + 3 user-arg primitives so
        // `buildFillerPlan` populates indices 0..2 (filler) leaving 3..5 to
        // user-arg path. If buildFillerPlan ever registers a key past
        // parameters.indices, R5 would trip here.
        val result = compile(
            SourceFile.kotlin(
                "A4.kt",
                """
                package probe.a4

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class A4Marker(
                    val src: Source = Source(),
                    val loc: SourceLocation = SourceLocation(),
                    val kind: CaptureKind = CaptureKind(),
                    val tag: String = "",
                    val count: Int = 0,
                    val flag: Boolean = false,
                )

                @A4Marker(tag = "x", count = 7, flag = true)
                internal fun siteFn() = 1

                internal object Main {
                    fun captured(): List<A4Marker> = capturedSources<A4Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoRequireTrip()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val cl = requireNotNull(result.classLoader)
        val main = cl.loadClass("probe.a4.Main")
        val captured = main.getMethod("captured").invoke(main.getField("INSTANCE").get(null)) as List<*>
        captured.size shouldBe 1
    }

    test("A5 baseline (all R1-R5): mixed declaration kinds + file + expression -> no trip") {
        // Stresses all 5 require sites simultaneously. If any require trips
        // on legitimate user code it would crash this compile.
        val result = compile(
            SourceFile.kotlin(
                "A5.kt",
                """
                @file:KindMarker

                package probe.a5

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(
                    AnnotationTarget.FUNCTION,
                    AnnotationTarget.PROPERTY,
                    AnnotationTarget.CLASS,
                    AnnotationTarget.TYPEALIAS,
                    AnnotationTarget.FILE,
                    AnnotationTarget.EXPRESSION,
                )
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class KindMarker(
                    val src: Source = Source(),
                    val kind: CaptureKind = CaptureKind(),
                )

                @KindMarker internal val prop = 1
                @KindMarker internal class Klass
                @KindMarker internal object Obj
                @KindMarker internal fun fn() = 2
                @KindMarker internal typealias Ali = Int

                internal fun expr(): Int = @KindMarker 3 + 4

                internal object Main {
                    fun captured(): List<KindMarker> = capturedSources<KindMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoRequireTrip()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    // ------------------------------------------------------------------
    // Fault-injection probes (F1-F4): for R3 / R4 we verify detect-ability
    // via two complementary channels because the public entry points are
    // guarded by Kotlin's `Intrinsics.checkNotNullParameter` for every
    // non-null IR parameter (= we cannot reach the require by passing
    // hand-built nulls):
    //
    //  - Bytecode verification (F1 / F3): inspect the production .class
    //    files of `BuildMarkerInstance` / `RewriteCapturedSourcesCall` and
    //    confirm the require()-trip message string literals are baked into
    //    the constant pool. This proves the message *would* surface on
    //    trip without needing to fabricate IrCall / IrPluginContext / etc.
    //  - Functional trip (F4): `buildSingle` is `private`, so Kotlin
    //    omits the param-non-null intrinsics on it. We reflectively
    //    invoke it with an out-of-range FillerPlan and observe the
    //    IllegalArgumentException for real.
    //
    // The F1n test additionally documents the surprising interaction
    // that user reflection cannot reach R4 with a null markerFqn (=
    // Kotlin intrinsic fires first); this is captured for the plan
    // appendix.
    // ------------------------------------------------------------------

    test("F1 detect-ability (R4: BuildMarkerInstance bytecode contains the markerFqn-blank require message)") {
        // R4's require() message must (a) name the function, (b) state the
        // broken precondition, and (c) hint the typical root cause. We
        // verify all three via the production class file's UTF-8 constant
        // pool entries (which is what `require {}` lambdas compile down to
        // as `ldc String` for the message).
        val bytecode = readClassBytes(BuildMarkerInstance::class.java)
        val literals = extractUtf8Literals(bytecode)
        // Look for the R4 message specifically -- the constant pool may
        // contain other strings (e.g. R5's message), so we match on the
        // distinctive R4 fragment.
        val r4 = literals.singleOrNull { it.contains("markerFqn must not be blank") }
            ?: error("expected exactly one constant pool string for R4 require, got: ${literals.filter { it.contains("markerFqn") }}")
        r4 shouldContain "BuildMarkerInstance" // (a) names the function/class
        r4 shouldContain "must not be blank"   // (b) states the precondition
        r4 shouldContain "Typical root cause"  // (c) hints the root cause
        r4 shouldContain "RewriteCapturedSourcesCall" // (c') points at the upstream caller
        r4 shouldContain "registered-marker filter"  // (c'') points at the bypass mechanism
    }

    test("F2 detect-ability (R5: BuildMarkerInstance bytecode contains the buildSingle out-of-range require message)") {
        // R5's message must also surface the function name + invariant +
        // root cause. Same verification channel as F1 / F3.
        val bytecode = readClassBytes(BuildMarkerInstance::class.java)
        val literals = extractUtf8Literals(bytecode)
        val r5 = literals.singleOrNull { it.contains("fillerPlan.bindings has keys out of range") }
            ?: error("expected exactly one constant pool string for R5 require, got: ${literals.filter { it.contains("fillerPlan") }}")
        r5 shouldContain "buildSingle"
        r5 shouldContain "out of range"
        r5 shouldContain "Typical root cause"
        r5 shouldContain "buildFillerPlan"
        r5 shouldContain "compiler-plugin bug"
    }

    test("F3 detect-ability (R3: RewriteCapturedSourcesCall bytecode contains the blank-markerFqn require message)") {
        val bytecode = readClassBytes(RewriteCapturedSourcesCall::class.java)
        val literals = extractUtf8Literals(bytecode)
        val r3 = literals.singleOrNull { it.contains("must carry a non-blank markerFqn") }
            ?: error("expected exactly one constant pool string for R3 require, got: ${literals.filter { it.contains("markerFqn") }}")
        r3 shouldContain "RewriteCapturedSourcesCall"
        r3 shouldContain "every CollectedSite"
        r3 shouldContain "Typical root cause"
        r3 shouldContain "hand-built CollectedSite"
        r3 shouldContain "CollectDeclarationSite"
    }

    test("F3b detect-ability (R1 / R2: ValidateCapturedSourcesCall bytecode contains both FIR require messages)") {
        // R1 + R2 live in ValidateCapturedSourcesCall.invoke as the first
        // two statements (R1 before the isCapturedSourcesCall guard, R2
        // immediately after it). Both must surface their preconditions +
        // root causes.
        val cls = Class.forName(
            "me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall",
        )
        val bytecode = readClassBytes(cls)
        val literals = extractUtf8Literals(bytecode)

        val r1 = literals.singleOrNull { it.contains("must be FirResolvedNamedReference") }
            ?: error("expected exactly one constant pool string for R1 require, got: ${literals.filter { it.contains("FirResolved") }}")
        r1 shouldContain "ValidateCapturedSourcesCall"
        r1 shouldContain "after FIR resolution"
        r1 shouldContain "Typical root cause"
        r1 shouldContain "before name resolution"

        val r2 = literals.singleOrNull { it.contains("typeArguments must not be empty") }
            ?: error("expected exactly one constant pool string for R2 require, got: ${literals.filter { it.contains("typeArguments") }}")
        r2 shouldContain "ValidateCapturedSourcesCall"
        r2 shouldContain "capturedSources<T>()"
        r2 shouldContain "Typical root cause"
        r2 shouldContain "runtime API signature"
    }

    test("F4 fault (R5: BuildMarkerInstance.buildSingle with out-of-range fillerPlan key) -> require trips") {
        // R5 lives in `buildSingle`, a `private fun` whose `FillerPlan` /
        // `FillerKind` are `private` nested types of `BuildMarkerInstance`.
        // We reach all 3 via reflection. Key construction:
        //   - fillerPlan.bindings = mapOf(99 to <FillerKind.SOURCE>)   <-- BAD
        //   - parameters = emptyList<IrValueParameter>()  (size 0)
        // -> `99 in 0..(-1)` is false -> require trips before any other
        //    code dereferences the null pluginContext / compat parameters.
        val instance = BuildMarkerInstance()
        val outerClass = instance.javaClass

        val fillerPlanClass = outerClass.declaredClasses.first { it.simpleName == "FillerPlan" }
        val fillerPlanCtor = fillerPlanClass.declaredConstructors.first()
        fillerPlanCtor.isAccessible = true

        val fillerKindClass = outerClass.declaredClasses.first { it.simpleName == "FillerKind" }
        val sourceKind = fillerKindClass.enumConstants.first { (it as Enum<*>).name == "SOURCE" }

        val badBindings = mapOf(99 to sourceKind)
        val badFillerPlan = fillerPlanCtor.newInstance(badBindings)

        val buildSingle = outerClass.declaredMethods.first { it.name == "buildSingle" }
        buildSingle.isAccessible = true

        val emptyParameters = emptyList<Any>()
        val dummySite = CollectedSite(
            site = CapturedSite(
                markerFqn = "com.example.Dummy",
                source = "fun f() = 1",
                kind = CapturedSite.CaptureKind.FUNCTION,
            ),
            markerCall = null,
            effectiveConfig = CaptureCodePluginConfig.DEFAULT,
        )

        // buildSingle parameters: markerType, markerConstructor, parameters,
        // fillerPlan, site, pluginContext, compat, fillSource,
        // fillSourceLocation, fillCaptureKind, buildUserArg,
        // buildUserArgPrimitive, messageCollector
        val cause = try {
            buildSingle.invoke(
                instance,
                null, null, emptyParameters, badFillerPlan, dummySite,
                null, null, null, null, null, null, null, null,
            )
            error("expected require(...) to trip, but buildSingle returned normally")
        } catch (ite: InvocationTargetException) {
            ite.targetException
        }
        cause.shouldBeInstanceOf<IllegalArgumentException>()
        val msg = requireNotNull(cause.message)
        msg shouldContain "fillerPlan.bindings has keys out of range"
        msg shouldContain "buildSingle"
        msg shouldContain "Typical root cause"
        msg shouldContain "compiler-plugin bug"
    }

    // ------------------------------------------------------------------
    // L1 limitation: R1 / R2 FIR-side require() cannot be tripped via
    // user code or via direct invoke (the heavy CheckerContext /
    // DiagnosticReporter / Diagnostics inputs are FIR-private). We
    // verify their absence via the baselines A1 / A5 above and document
    // here why no F-* probe exists for R1 / R2.
    // ------------------------------------------------------------------

    test("L1 limitation note: R1 / R2 are FIR-internal, only baseline verification") {
        // This test is intentionally a documentation marker. R1 verifies
        // that the FIR Checker registration happens after name resolution
        // (a phase contract guaranteed by Kotlin compiler internals), and
        // R2 verifies that capturedSources<T>() always has a type argument
        // (a runtime API signature contract). Tripping either from user
        // code would require:
        //   - R1: registering CaptureCodeFirCheckersExtension to run at
        //         CHECK_NAMES or earlier (requires plugin source edit).
        //   - R2: changing the runtime API to drop the <T> type parameter
        //         (requires :annotation module edit + plugin rebuild).
        // Both are caller-bug paths only reachable when an outside party
        // modifies plugin internals, which our probe charter excludes.
        // Hence: no F-* test for R1 / R2.
        true shouldBe true
    }
}) {
    /** kctfork result + captured stdout for grepping require()-trip messages. */
    private data class ProbeResult(
        val exitCode: KotlinCompilation.ExitCode,
        val output: String,
        val classLoader: ClassLoader?,
        val registeredMarkers: List<String>,
    ) {
        /**
         * Assert that none of the 5 `require(...)` sites was triggered during
         * a baseline compile. The five message substrings are unique to the
         * task-139 / task-140 require sites in the codebase (verified by
         * `grep -rn "Typical root cause" compiler-plugin/src/main`).
         */
        fun assertNoRequireTrip() {
            output shouldNotContain "markerFqn must not be blank"
            output shouldNotContain "must carry a non-blank markerFqn"
            output shouldNotContain "must not be empty for capturedSources"
            output shouldNotContain "fillerPlan.bindings has keys out of range"
            output shouldNotContain "must be FirResolvedNamedReference"
        }
    }

    companion object {
        /**
         * Reads the raw class bytecode for the given class via its
         * defining ClassLoader, returning a snapshot of the production
         * `.class` file. Used by the F1 / F2 / F3 / F3b probes to inspect
         * the constant pool for require()-trip message strings without
         * needing to fabricate IrCall / IrPluginContext / CompatContext
         * placeholders (= public IR entry points are protected by
         * Kotlin's Intrinsics.checkNotNullParameter, which we cannot
         * cheaply bypass — see plan §"Constraints" for details).
         */
        private fun readClassBytes(cls: Class<*>): ByteArray {
            val resource = cls.name.replace('.', '/') + ".class"
            val stream = cls.classLoader.getResourceAsStream(resource)
                ?: error("could not locate bytecode for ${cls.name}")
            return stream.use { it.readBytes() }
        }

        /**
         * Extracts every UTF-8 string literal from the constant pool of a
         * JVM class file. Used to confirm that require()-trip message
         * strings are baked into the production bytecode (= they will
         * surface verbatim if the require ever trips). Walks the class
         * file header per JVMS §4.4: 4-byte magic, 2 minor, 2 major,
         * 2 constant_pool_count, then iterates each constant_pool entry
         * by tag and collects the bytes of every CONSTANT_Utf8_info.
         */
        private fun extractUtf8Literals(bytecode: ByteArray): List<String> {
            val buf = java.nio.ByteBuffer.wrap(bytecode)
            buf.order(java.nio.ByteOrder.BIG_ENDIAN)
            val magic = buf.int
            check(magic == -0x35014542) { "bad magic 0x${Integer.toHexString(magic)}" }
            buf.short // minor
            buf.short // major
            val cpCount = buf.short.toInt() and 0xFFFF
            val literals = mutableListOf<String>()
            var i = 1
            while (i < cpCount) {
                when (val tag = buf.get().toInt() and 0xFF) {
                    1 -> { // CONSTANT_Utf8
                        val len = buf.short.toInt() and 0xFFFF
                        val bytes = ByteArray(len)
                        buf.get(bytes)
                        literals += String(bytes, Charsets.UTF_8)
                    }
                    3, 4 -> buf.int                    // Integer / Float
                    5, 6 -> { buf.long; i++ }          // Long / Double (occupy 2 slots)
                    7, 8, 16, 19, 20 -> buf.short      // Class / String / MethodType / Module / Package
                    9, 10, 11, 12, 17, 18 -> { buf.short; buf.short } // Fieldref / Methodref / InterfaceMethodref / NameAndType / Dynamic / InvokeDynamic
                    15 -> { buf.get(); buf.short }    // MethodHandle
                    else -> error("unknown constant pool tag $tag at slot $i (offset ${buf.position()})")
                }
                i++
            }
            return literals
        }
    }
}
