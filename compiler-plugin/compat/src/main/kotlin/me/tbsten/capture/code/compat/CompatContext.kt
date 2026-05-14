package me.tbsten.capture.code.compat

import java.util.ServiceLoader
import me.tbsten.capture.code.CaptureCodePluginConfig
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * SPI that absorbs version-specific differences in the Kotlin compiler API
 * for the Capture-Code compiler plugin.
 *
 * Implementations are registered as a [Factory] in
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory`, and
 * [CompatContext.Companion.load] picks the best one at runtime based on the
 * current Kotlin compiler version (Metro / compose-preview-lab pattern).
 *
 * Each abstract method here corresponds to a known API drift point between
 * supported Kotlin versions:
 *
 * - [transformIr] — unifies the IR transformation entry point that used to be
 *   `IrInjector.transform`. Each compat module compiles its IR rewriting logic
 *   against its own Kotlin baseline (drift D5–D8).
 * - [literalValueOrNull] / [isLiteralExpression] — absorbs the
 *   `FirLiteralExpression<T>` → `FirLiteralExpression` type-parameter removal
 *   drift (D1).
 * - [toRegularClassSymbolOrNull] — absorbs the
 *   `ConeKotlinType.toRegularClassSymbol(session)` extension's package move
 *   from `fir.types` (2.0.x) to `fir.resolve` (2.1.x) (D2).
 * - [classIdOf] — guards the `FirRegularClassSymbol.classId` accessor against
 *   any future shape change (D3).
 *
 * When a new Kotlin version introduces additional drift, add an abstract
 * method here and implement it in each `compat-kXXX` module.
 */
public interface CompatContext {

    /**
     * Runs the version-specific IR rewriting pass over [moduleFragment].
     *
     * Each compat module owns the IR walker / rewriter pair that consumes
     * [CaptureCodeMarkerRegistry] (FIR-side) and
     * [CaptureCodeExpressionSiteRegistry] (FIR-side) and produces the
     * `listOf(T(...))` replacement for every `capturedSources<T>()` call.
     *
     * **Before**: `moduleFragment` with unmodified `capturedSources<T>()` calls
     * that return a stub `listOf()` at runtime.
     *
     * **After**: `capturedSources<T>()` is replaced with
     * `listOf(T(Source(...), SourceLocation(...), ...), ...)` for every
     * declaration / file / expression annotated with a `@CaptureCode`-meta
     * marker, with filler values resolved from the captured site's IR.
     */
    public fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    )

    /**
     * Returns the resolved compile-time value of [expression] if it is a
     * literal (Int / Long / String / Boolean / Double / Float / Char / etc.),
     * otherwise `null`.
     *
     * Absorbs the `FirLiteralExpression<T>` → `FirLiteralExpression` type
     * parameter removal in Kotlin 2.0.21+. Each compat module dispatches on
     * the concrete FIR class shape that matches its baseline.
     *
     * **Sample call**: `compat.literalValueOrNull(fir.argumentMapping.mapping["label"]!!)`
     *
     * **Result**: `"hello"` for `@Marker(label = "hello")`, `null` for non-literal
     * arguments such as a function call or property reference.
     */
    public fun literalValueOrNull(expression: FirExpression): Any?

    /** True iff [expression] is a (version-appropriate) FIR literal expression. */
    public fun isLiteralExpression(expression: FirExpression): Boolean

    /**
     * Resolves [type] to its [FirRegularClassSymbol] using [session], or `null`
     * if it cannot be resolved (star projection, type inference failure, etc.).
     *
     * Absorbs drift D2: the `ConeKotlinType.toRegularClassSymbol(session)`
     * extension moved from `org.jetbrains.kotlin.fir.types` (2.0.x) to
     * `org.jetbrains.kotlin.fir.resolve` (2.1.x). Each compat module imports the
     * extension from the location available on its baseline.
     *
     * **Sample call**: `compat.toRegularClassSymbolOrNull(coneType, session)`
     *
     * **Result**: the `FirRegularClassSymbol` for `coneType` (e.g. resolves
     * `com.example.MySnippet` to its `FirRegularClassSymbol`), or `null`.
     */
    public fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol?

    /**
     * Returns [symbol]'s [ClassId], or `null` if it does not have one.
     *
     * Currently a thin wrapper over `FirRegularClassSymbol.classId`, kept here
     * to guard against a future shape change of that accessor (drift D3).
     */
    public fun classIdOf(symbol: FirRegularClassSymbol): ClassId?

    /**
     * Returns the list of FIR `FirAdditionalCheckersExtension` factories that
     * this compat implementation contributes to the plugin's
     * `FirExtensionRegistrar`.
     *
     * Each element is a function `(FirSession) -> FirAdditionalCheckersExtension`
     * that the main module's `CaptureCodeFirExtensionRegistrar` registers via
     * `+plusAdditionalCheckersExtension(...)`.
     *
     * **Why factories instead of pre-built extensions**: Kotlin builds one FIR
     * session per compile and constructs extensions per-session. The factory is
     * invoked with the active `FirSession` to produce a fresh extension instance
     * each time.
     *
     * **Why this lives in compat layer**: `FirRegularClassChecker` /
     * `FirBasicExpressionChecker` (= `FirDeclarationChecker<FirRegularClass>` /
     * `FirExpressionChecker<FirStatement>`) are abstract base classes whose
     * `check(...)` method's **argument order** shifted across Kotlin minor
     * versions:
     *
     * - 2.0.x: `check(declaration, context, reporter)`
     * - 2.2.x+: `check(context, reporter, declaration)`
     *
     * The bytecode produced by compiling a checker against 2.0 will fail to
     * dispatch to the 2.2 abstract method at runtime (AbstractMethodError).
     * By keeping each version's checker concrete subclasses inside the matching
     * `compat-kXXX` module (compiled against its own baseline), the main module
     * never holds a static reference to a `FirChecker` subclass and the runtime
     * drift is isolated to the compat layer.
     *
     * Implementations typically return checker extensions covering:
     * - Logic A: `@CaptureCode` marker class discovery & registration
     * - Logic F: marker annotation constraint diagnostics
     * - Logic G: `capturedSources<T>()` type argument validation
     * - Logic B-fir: expression-site `@Marker (expr)` collection
     */
    public fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension>

    /**
     * Factory for compat implementations. Each `compat-kXXX` module registers
     * its implementation (with its own [minVersion]) in
     * `META-INF/services/me.tbsten.capture.code.compat.CompatContext${'$'}Factory`.
     */
    public interface Factory {
        /**
         * The minimum Kotlin version this implementation supports
         * (e.g. "2.0.0", "2.1.0-Beta2", "2.1.0-dev-2124").
         */
        public val minVersion: String

        /** Creates and returns the [CompatContext] implementation. */
        public fun create(): CompatContext
    }

    public companion object Companion {

        /**
         * Loads the [CompatContext] implementation that best matches the current
         * Kotlin compiler version.
         *
         * Resolution rules (Metro pattern):
         *
         * 1. If the current version is a `dev` build, look for `dev` track
         *    factories first and compare within the dev track. This prevents a
         *    `2.1.0-dev-7791` build from accidentally falling onto a
         *    `2.1.0-Beta1` factory (since `Beta > DEV` in maturity ordering).
         * 2. If no dev factory matches, fall back to non-dev factories using
         *    the base version stripped of the dev classifier (because
         *    `2.1.0-dev-5812` is still semantically *a build of* `2.1.0`).
         * 3. For non-dev current versions, only non-dev factories are
         *    considered, and the one with the largest `minVersion <= current`
         *    wins.
         *
         * @param knownVersion for tests / explicit selection. When `null`, the
         *   Kotlin compiler version is read from `META-INF/compiler.version`
         *   in the `kotlin-compiler-embeddable` jar.
         */
        public fun load(knownVersion: KotlinToolingVersion? = null): CompatContext =
            resolveFactory(knownVersion).create()

        /**
         * Same as [load] but accepts a raw version string (e.g.
         * `KotlinCompilerVersion.VERSION`).
         */
        public fun load(currentKotlinVersion: String): CompatContext =
            load(KotlinToolingVersion(currentKotlinVersion))

        internal fun resolveFactory(
            knownVersion: KotlinToolingVersion?,
            factories: Sequence<Factory> = loadFactories(),
        ): Factory {
            val factoryList = factories.toList()
            if (factoryList.isEmpty()) {
                error(
                    "No CompatContext.Factory implementations found on the classpath. " +
                        "The :compiler-plugin:compat-k* modules may not be bundled into the published plugin jar.",
                )
            }

            val currentVersion =
                knownVersion ?: detectCurrentKotlinVersion()
                    ?: error(
                        "Could not detect Kotlin compiler version (META-INF/compiler.version not found). " +
                            "Available factories: ${factoryList.map { it.minVersion }}",
                    )

            return resolveFactoryForVersion(currentVersion, factoryList)
                ?: error(
                    """
                    Unrecognized Kotlin version: $currentVersion
                    Available factories: ${factoryList.map { it.minVersion }}
                    """.trimIndent(),
                )
        }

        private fun loadFactories(): Sequence<Factory> =
            ServiceLoader
                .load(Factory::class.java, Factory::class.java.classLoader)
                .asSequence()

        private fun resolveFactoryForVersion(
            currentVersion: KotlinToolingVersion,
            factories: List<Factory>,
        ): Factory? {
            // If current version is DEV, try DEV-track factories first.
            if (currentVersion.isDev) {
                val devFactories = factories.filter {
                    runCatching { KotlinToolingVersion(it.minVersion).isDev }.getOrDefault(false)
                }
                val devMatch = findHighestCompatibleFactory(currentVersion, devFactories)
                if (devMatch != null) return devMatch

                // Fall back to non-DEV factories using the base version
                // (`2.1.0-dev-5812` is a dev build OF `2.1.0`).
                val nonDevFactories = factories.filter {
                    runCatching { !KotlinToolingVersion(it.minVersion).isDev }.getOrDefault(false)
                }
                val baseVersion = KotlinToolingVersion(
                    major = currentVersion.major,
                    minor = currentVersion.minor,
                    patch = currentVersion.patch,
                    classifier = null,
                )
                return findHighestCompatibleFactory(baseVersion, nonDevFactories)
            }

            // For non-DEV current versions, only non-DEV factories.
            val nonDevFactories = factories.filter {
                runCatching { !KotlinToolingVersion(it.minVersion).isDev }.getOrDefault(false)
            }
            return findHighestCompatibleFactory(currentVersion, nonDevFactories)
        }

        private fun findHighestCompatibleFactory(
            currentVersion: KotlinToolingVersion,
            factories: List<Factory>,
        ): Factory? = factories
            .filter {
                runCatching { currentVersion >= KotlinToolingVersion(it.minVersion) }
                    .getOrDefault(false)
            }
            .maxByOrNull { KotlinToolingVersion(it.minVersion) }

        /**
         * Reads the Kotlin compiler version from `META-INF/compiler.version`
         * shipped inside `kotlin-compiler-embeddable.jar`.
         */
        private fun detectCurrentKotlinVersion(): KotlinToolingVersion? {
            val classLoader = CompatContext::class.java.classLoader ?: return null
            val resource = classLoader.getResourceAsStream("META-INF/compiler.version") ?: return null
            val text = resource.use { it.bufferedReader().readText().trim() }
            if (text.isEmpty()) return null
            return runCatching { KotlinToolingVersion(text) }.getOrNull()
        }
    }
}
