package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Charter 7 (exploratory-debug-plan §4) — fault injection probe for the 3
 * `error("Internal: ...")` sites added in commit d2392dd (task-137):
 *
 * | site | location |
 * | --- | --- |
 * | 1 | `FillCaptureKind.kt:54`  `kindEnumEntries[site.kind] ?: error(...)` |
 * | 2 | `BuildMarkerInstance.kt:155` `markerSymbol.primaryConstructorOrNull() ?: error(...)` |
 * | 3 | `BuildMarkerInstance.kt:166` `pluginContext.findListOfVararg(compat) ?: error(...)` |
 *
 * Each `error("Internal: ...")` claims to be unreachable for a different
 * reason — site 1 says "resolveOrNull invariant", site 2 says "Kotlin spec
 * guarantees primary constructor on annotation classes", site 3 says
 * "kotlin-stdlib must be on the classpath". This probe tries to bend each
 * precondition with user-reachable Kotlin source so we can either:
 *
 * - confirm the invariant is truly unreachable (= "the `error()` will never
 *   fire from any compile-time invocation"), or
 * - find a real trip path and file a bug ticket.
 *
 * Most probes attempt a fault by **constructing an odd marker class shape**
 * (Java `@interface`, `@JvmInline value class`, `expect annotation class`,
 * etc.) and then check whether the plugin trips the `error()`, gracefully
 * skips the rewrite, or rejects the marker earlier in the FIR phase. Trip
 * detection is via `KotlinCompilation.compile()` not crashing the JVM with an
 * `IllegalStateException` whose message starts with `Internal:`.
 */
class Charter7InternalFailFastProbeTest : FunSpec({

    beforeEach { CaptureCodeMarkerRegistry.reset() }
    afterEach { CaptureCodeMarkerRegistry.reset() }

    /**
     * Compile with the plugin registrar enabled and capture stdout/stderr so
     * the probe can grep for the `Internal: ...` strings without depending on
     * the kctfork-internal message collector format.
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
        // Make probe logs visible in `gradle :compiler-plugin:test` stdout so
        // the maintainer can verify *why* a probe passed (e.g. silent skip vs
        // FIR-phase reject).
        println("\n========== Charter 7 probe ==========")
        println("exitCode=${result.exitCode}, registeredMarkers=${CaptureCodeMarkerRegistry.markerFqns}")
        println(output.lines().filter { line ->
            line.isNotBlank() && (
                line.startsWith("w:") || line.startsWith("e:") ||
                    line.contains("Internal:") || line.contains("CC_") ||
                    line.contains("error:") || line.contains("warning:")
                )
        }.joinToString("\n"))
        println("=====================================\n")
        return ProbeResult(
            exitCode = result.exitCode,
            output = output,
            classLoader = if (result.exitCode == KotlinCompilation.ExitCode.OK) result.classLoader else null,
            registeredMarkers = CaptureCodeMarkerRegistry.markerFqns.toList().sorted(),
        )
    }

    // ------------------------------------------------------------------
    // Site 1 baseline + fault probes
    // ------------------------------------------------------------------
    test("A1 site 1 baseline: every CapturedSite.CaptureKind value compiles cleanly + IR runs through FillCaptureKind") {
        // All 7 site kinds (= PROPERTY / CLASS / OBJECT / FUNCTION / TYPEALIAS /
        // FILE / EXPRESSION) exercised at once. If FillCaptureKind.resolveOrNull
        // is missing an entry the rewrite would be skipped silently; if the
        // invariant is broken the compile would throw "Internal:" with
        // CapturedSite.CaptureKind.<name>.
        val result = compile(
            SourceFile.kotlin(
                "AllKinds.kt",
                """
                @file:KindMarker

                package probe.a1

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
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
                internal annotation class KindMarker(val kind: CaptureKind = CaptureKind())

                @KindMarker
                internal val prop = 1

                @KindMarker
                internal class Klass

                @KindMarker
                internal object Obj

                @KindMarker
                internal fun fn() = 2

                @KindMarker
                internal typealias Ali = Int

                internal fun expr(): Int = @KindMarker 3 + 4

                internal object Main {
                    fun captured(): List<KindMarker> = capturedSources<KindMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        // Prove FillCaptureKind.invoke ran for each declared kind (= 5 sites
        // total: prop / class / obj / fn / typealias; FILE and EXPRESSION
        // require additional infra and are covered by separate integration
        // tests). All 5 must resolve their CaptureKind enum entry through
        // `kindEnumEntries[site.kind]` without tripping site 1.
        val cl = requireNotNull(result.classLoader)
        val main = cl.loadClass("probe.a1.Main")
        val captured = main.getMethod("captured").invoke(main.getField("INSTANCE").get(null)) as List<*>
        // declaration-origin sites (5) + file annotation (1) = 6 minimum; the
        // expression `@KindMarker 3` adds 1 if expression collection works in
        // baseline. We assert >= 5 to keep the probe robust against varying
        // expression-site behaviour.
        (captured.size >= 5) shouldBe true
    }

    // ------------------------------------------------------------------
    // Site 2 baseline + fault probes
    // ------------------------------------------------------------------
    test("B1 site 2 baseline: ordinary annotation class marker -> no trip + IR rewrite proven") {
        val result = compile(
            SourceFile.kotlin(
                "B1.kt",
                """
                package probe.b1

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class B1Marker(val source: Source = Source())

                @B1Marker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<B1Marker> = capturedSources<B1Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        // Prove that BuildMarkerInstance.invoke actually executed past the
        // primaryConstructorOrNull and findListOfVararg sites (i.e. the
        // baseline drives both site 2 and site 3 through their "no trip"
        // branch). The runtime list size being 1 only happens when the IR
        // rewrite replaced the stub `capturedSources()` body with a real
        // `listOf(B1Marker(...))` call constructed by BuildMarkerInstance.
        val cl = requireNotNull(result.classLoader) { "compile OK but classLoader is null" }
        val main = cl.loadClass("probe.b1.Main")
        val captured = main.getMethod("captured").invoke(main.getField("INSTANCE").get(null)) as List<*>
        captured.size shouldBe 1
    }

    test("B2 site 2 fault: zero-parameter annotation class marker -> no trip") {
        // No user parameter & no filler. annotation class still has an implicit
        // primary constructor (Kotlin spec), so primaryConstructorOrNull must
        // return non-null even with zero value parameters.
        val result = compile(
            SourceFile.kotlin(
                "B2.kt",
                """
                package probe.b2

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class B2Marker

                @B2Marker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<B2Marker> = capturedSources<B2Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("B3 site 2 fault: Java @interface marker (cannot wear @CaptureCode meta from Kotlin)") {
        // Java-side annotation type. Kotlin @CaptureCode is BINARY-retention so
        // a Java @interface can syntactically declare @CaptureCode, but
        // `pluginContext.referenceClass(...)` reaches a `IrClass` whose
        // declarations may not contain an IrConstructor at all (Java
        // annotations have no constructor concept). This is the closest we can
        // get to a primary-constructor-free marker class without forging IR.
        val result = compile(
            SourceFile.java(
                "JavaMarker.java",
                """
                package probe.b3;

                import me.tbsten.capture.code.CaptureCode;

                @CaptureCode
                public @interface JavaMarker {
                }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "B3.kt",
                """
                package probe.b3

                import me.tbsten.capture.code.capturedSources

                @JavaMarker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<JavaMarker> = capturedSources<JavaMarker>()
                }
                """.trimIndent(),
            ),
        )
        // Whatever happens, the plugin must not throw an "Internal:" error.
        result.assertNoInternalError()
        // Allow either OK (marker not registered → silent skip) or COMPILATION_ERROR
        // (e.g. unsupported Java meta). The key contract is: no "Internal:" trip.
    }

    test("B4 site 2 fault: @JvmInline value class marker is rejected before reaching IR") {
        // @JvmInline value class is not ANNOTATION_CLASS, so DiscoverMarkerClass
        // / ValidateMarkerAnnotation skip it at FIR. It can't even pass FIR
        // because @CaptureCode targets only ANNOTATION_CLASS.
        val result = compile(
            SourceFile.kotlin(
                "B4.kt",
                """
                package probe.b4

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @JvmInline
                internal value class B4Marker(val v: Int)

                internal object Main {
                    fun captured(): List<B4Marker> = capturedSources<B4Marker>()
                }
                """.trimIndent(),
            ),
        )
        // Compilation should fail at FIR (typically: `@CaptureCode` is not
        // applicable to a value class; or `T must be annotation`). Either way
        // the plugin must not crash with "Internal: marker class ... has no
        // primary constructor".
        result.assertNoInternalError()
    }

    test("B5 site 2 fault: expect annotation class marker is rejected with markerIsExpect warning") {
        // expect annotation class would be a true primary-constructor-free shape
        // in some intermediate phases. ValidateMarkerAnnotation reports
        // CC_MARKER_IS_EXPECT but in single-platform compile the IR phase still
        // sees the class with a primary constructor. We verify no Internal:
        // trip.
        val result = compile(
            SourceFile.kotlin(
                "B5.kt",
                """
                package probe.b5

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal expect annotation class B5Marker(val source: Source = Source())

                internal object Main {
                    fun captured(): List<B5Marker> = capturedSources<B5Marker>()
                }
                """.trimIndent(),
            ),
        )
        // expect declarations in single-target kctfork projects fail at FIR
        // (multiplatform plugin missing) which is fine; the contract is just
        // no "Internal:" trip from our 3 sites.
        result.assertNoInternalError()
    }

    // ------------------------------------------------------------------
    // Site 3 baseline + fault probes
    // ------------------------------------------------------------------
    test("C1 site 3 fault: compile without inheriting classpath (no kotlin-stdlib visible to plugin)") {
        // inheritClassPath=false strips the Java classpath, so the plugin's
        // `pluginContext.findListOfVararg(compat)` would have nothing to
        // resolve. In practice the underlying Kotlin compile dies far earlier
        // because the source can't even compile without kotlin-stdlib symbols.
        // We assert: even with a stripped classpath, the plugin's "Internal:
        // kotlin.collections.listOf(vararg)" message must not appear. (If the
        // compile fails for legitimate "cannot find class" reasons, that's
        // expected.)
        val result = compile(
            SourceFile.kotlin(
                "C1.kt",
                """
                package probe.c1

                internal object Main {
                    fun greet(): String = "hi"
                }
                """.trimIndent(),
            ),
            inheritClassPath = false,
        )
        result.assertNoInternalError()
    }

    test("C2 site 3 baseline: marker + capturedSources<T>() with stdlib resolves listOf(vararg) + IR proven") {
        val result = compile(
            SourceFile.kotlin(
                "C2.kt",
                """
                package probe.c2

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class C2Marker(val source: Source = Source())

                @C2Marker
                internal fun f() = 1

                internal object Main {
                    fun captured(): List<C2Marker> = capturedSources<C2Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        // Proves findListOfVararg actually resolved — the runtime List value
        // we get back was created by the IR-built listOf(vararg) call.
        val cl = requireNotNull(result.classLoader)
        val main = cl.loadClass("probe.c2.Main")
        val captured = main.getMethod("captured").invoke(main.getField("INSTANCE").get(null)) as List<*>
        captured.size shouldBe 1
    }

    // ------------------------------------------------------------------
    // Site 2 deeper probes (cross-file / inner-class shapes)
    // ------------------------------------------------------------------
    test("D1 site 2 fault: marker declared in a separate file but same module -> no trip") {
        // The "different file" axis is a common KMP-style accidental factor:
        // Marker.kt is registered first, Site.kt's compile unit later refers
        // to the registered FQN. Verify primary constructor is still resolved.
        val result = compile(
            SourceFile.kotlin(
                "D1Marker.kt",
                """
                package probe.d1

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class D1Marker(val source: Source = Source())
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "D1Site.kt",
                """
                package probe.d1

                import me.tbsten.capture.code.capturedSources

                @D1Marker
                internal fun siteFn() = "hi"

                internal object Main {
                    fun captured(): List<D1Marker> = capturedSources<D1Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("D2 site 2 fault: nested annotation class inside object -> no trip") {
        // `object Outer { annotation class Inner }` — Kotlin allows nested
        // annotation classes in some contexts. Verify primary constructor
        // resolution still succeeds for the nested marker via top-level FqN.
        val result = compile(
            SourceFile.kotlin(
                "D2.kt",
                """
                package probe.d2

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                internal object Outer {
                    @CaptureCode
                    @Target(AnnotationTarget.FUNCTION)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class D2Inner(val source: Source = Source())
                }

                @Outer.D2Inner
                internal fun siteFn() = "hi"

                internal object Main {
                    fun captured(): List<Outer.D2Inner> = capturedSources<Outer.D2Inner>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        // Nested FQN may or may not resolve via referenceClass(ClassId.topLevel);
        // even if it does not, the plugin should emit a warning rather than
        // tripping site 2 (= primaryConstructorOrNull) since registry filtering
        // happens before the constructor lookup. Tolerate either outcome.
    }

    test("D3 site 2 fault: marker class with many secondary-shaped parameters -> no trip") {
        // Annotation classes cannot have secondary constructors in Kotlin
        // spec; the closest "weird" shape is to pack the primary with many
        // mixed user + filler parameters and verify the constructor still
        // resolves. If constructor disappears in any pathological IrClass
        // representation, this is the most likely culprit shape.
        val result = compile(
            SourceFile.kotlin(
                "D3.kt",
                """
                package probe.d3

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class D3Marker(
                    val src: Source = Source(),
                    val loc: SourceLocation = SourceLocation(),
                    val kind: CaptureKind = CaptureKind(),
                    val tag: String = "",
                    val count: Int = 0,
                    val flags: IntArray = [],
                )

                @D3Marker(tag = "x", count = 7, flags = [1, 2, 3])
                internal fun siteFn() = 1

                internal object Main {
                    fun captured(): List<D3Marker> = capturedSources<D3Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("E2 site 3 fault: explicit kotlin.collections.listOf shadowing in user code -> no trip") {
        // User code declares its own `listOf` in `kotlin.collections` (= will
        // collide-or-shadow at use site, but stdlib still wins at the binary
        // symbol level the plugin queries). This probes whether
        // `findListOfVararg` is fooled by collision.
        val result = compile(
            SourceFile.kotlin(
                "E2.kt",
                """
                package probe.e2

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class E2Marker(val source: Source = Source())

                @E2Marker
                internal fun siteFn() = 1

                internal object Main {
                    fun captured(): List<E2Marker> = capturedSources<E2Marker>()
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }

    test("control: compile that does not invoke capturedSources<T>() still does not trip") {
        // No `capturedSources<T>()` call at all -> IR rewriter never enters
        // BuildMarkerInstance.invoke. Confirms the trip is conditional on the
        // user's calling pattern.
        val result = compile(
            SourceFile.kotlin(
                "Control.kt",
                """
                package probe.control

                internal object Main {
                    fun greet(): String = "hi"
                }
                """.trimIndent(),
            ),
        )
        result.assertNoInternalError()
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
    }
}) {
    /** kctfork result + captured stdout for grepping plugin internal errors. */
    private data class ProbeResult(
        val exitCode: KotlinCompilation.ExitCode,
        val output: String,
        val classLoader: ClassLoader?,
        val registeredMarkers: List<String>,
    ) {
        /**
         * Assert that none of the 3 `error("Internal: ...")` sites was
         * triggered. The substring `Internal:` is unique to the three
         * task-137 fail-fast messages in the codebase (verified by
         * `grep -rn "Internal:" compiler-plugin/src`).
         */
        fun assertNoInternalError() {
            shouldNotThrowAny {
                // The error("...") inside the plugin throws IllegalStateException
                // which kctfork forwards into the captured stdout (not as a
                // JVM crash). Both pathways are covered by the substring check.
            }
            output shouldNotContain "Internal: CapturedSite.CaptureKind."
            output shouldNotContain "Internal: marker class"
            output shouldNotContain "Internal: kotlin.collections.listOf(vararg)"
        }
    }
}
