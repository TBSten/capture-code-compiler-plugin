package me.tbsten.capture.code.compat

import java.util.ServiceLoader
import me.tbsten.capture.code.CaptureCodePluginConfig
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
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
 *
 * ## API surface decision (task-117, 2026-05-16)
 *
 * Option 2 (keep the existing low-level surface) was chosen over goals.md §2.3's
 * high-level `installExtensions(firCheckers, irTransformer)` proposal. Rationale:
 * locking the interface in before task-119+ produces concrete FIR/IR logic classes
 * would freeze design choices (e.g. `DiagnosticContext` shape) prematurely, and the
 * current surface can already aggregate logic via per-`compat-kXXX` checker wiring.
 * See the developer-internal note `.local/tmp/api-surface-investigation.md`
 * (gitignored) for the 3-option comparison. This decision is revisitable additively
 * in a later task.
 *
 * So this file keeps its low-level method surface (Option 2). Downstream tasks
 * (task-118+) only touch implementations, with the additive exception that new
 * drift-absorbing methods may be appended here as new drift points emerge
 * (e.g. `containingFilePathOf` / `fullyExpandedTypeOf` added in task-119 to
 * absorb drift D11 / D12).
 */
public interface CompatContext {

    /**
     * Runs the version-specific IR rewriting pass over [moduleFragment].
     *
     * Each compat module owns the IR walker / rewriter pair that consumes
     * [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry] (FIR-side) and
     * [me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry] (FIR-side) and produces the
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
     * Returns the containing file path of the FIR [context], or `null` if the
     * checker is not currently positioned inside a file with a known path.
     *
     * Absorbs drift D12 (`CheckerContext.containingFile` removal):
     * - 2.0.x .. 2.2.x: `context.containingFile?.sourceFile?.path`
     * - 2.3.x+: the `containingFile: FirFile?` accessor was removed and
     *   replaced with a flat `containingFilePath: String?` property.
     *
     * Each compat module dispatches to the accessor that exists on its own
     * baseline. Main-module logic (compiled against the 2.0.0 baseline)
     * cannot reference either accessor directly without breaking at runtime
     * on the other side of the drift, so it routes through this method.
     *
     * **Sample call**: `compat.containingFilePathOf(checkerContext)`
     *
     * **Result**: e.g. `"/Users/foo/Sample.kt"`, or `null` if the checker
     * is firing in a context without an associated source file.
     */
    public fun containingFilePathOf(context: CheckerContext): String?

    /**
     * Returns [type] with all type aliases fully expanded, or [type] unchanged
     * if expansion is not safely available.
     *
     * Absorbs drift D11 (`TypeExpansionUtilsKt.fullyExpandedType` overload change):
     * - 2.0.0 .. 2.0.10: the 2-arg overload `(ConeKotlinType, FirSession) -> ConeKotlinType`
     *   is the only available form.
     * - 2.0.20+: the 2-arg overload was removed; only a 3-arg overload with an
     *   optional `Function1` parameter is exposed
     *   (`fullyExpandedType(ConeKotlinType, FirSession, Function1)`).
     * - 2.1.x+: the surviving 3-arg overload accepts a default lambda so the
     *   Kotlin call site `coneType.fullyExpandedType(session)` re-binds cleanly.
     *
     * Main-module logic (compiled against the 2.0.0 baseline) calling
     * `coneType.fullyExpandedType(session)` would emit an `INVOKESTATIC` to
     * the now-removed 2-arg overload, yielding `NoSuchMethodError` on 2.0.20+
     * (task-080). Going through this compat method lets each `compat-kXXX`
     * module use the overload available on its own baseline (reflection shim
     * inside compat-k200 / compat-k202, direct call inside compat-k210+).
     */
    public fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType

    /**
     * Loads the raw text of the given [IrFile], using PSI when available and
     * file-system fallback otherwise. Returns `null` if neither path resolves to
     * usable text.
     *
     * Absorbs drift around `IrFileEntry` / `PsiIrFileEntry` PSI accessors which
     * shift between Kotlin minor versions. Main-module logic that needs the file
     * source text (Logic C `ExtractSourceText`, Logic B-ir `CollectDeclarationSite`,
     * Logic D `NormalizeSource` wiring) goes through this method so that PSI access
     * stays inside each `compat-kXXX` module compiled against its own baseline.
     *
     * Added in task-120 (IR logic migration to main).
     */
    public fun loadFileText(file: IrFile): String?

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
     * Registers the plugin's FIR registrar adapter and IR generation extension
     * onto the supplied [extensionStorage].
     *
     * **Why this lives in the compat layer (drift D10)**:
     * The signatures of `CompilerPluginRegistrar.ExtensionStorage.registerExtension(...)`
     * and the related `Companion` objects (`FirExtensionRegistrarAdapter.Companion`,
     * `IrGenerationExtension.Companion`) changed their **super class** between
     * Kotlin 2.2.x and 2.3.x:
     *
     * - 2.0.x .. 2.2.x: `Companion : ProjectExtensionDescriptor<...>`
     *   `ExtensionStorage.registerExtension(ProjectExtensionDescriptor<T>, T)`
     * - 2.3.x .. 2.4.x: `Companion : ExtensionPointDescriptor<...>`
     *   `ExtensionStorage.registerExtension(ExtensionPointDescriptor<T>, T)`
     *
     * Bytecode compiled against 2.0.x baseline still references
     * `registerExtension(ProjectExtensionDescriptor, Object)` and casts the
     * Companion to `ProjectExtensionDescriptor`, both of which fail at runtime
     * on 2.3.x+ (NoSuchMethodError / ClassCastException). Since each
     * `compat-kXXX` module is compiled against its native
     * `kotlin-compiler-embeddable`, delegating registration to the compat layer
     * resolves the call site against the correct signature for the current
     * Kotlin runtime.
     *
     * The [extensionStorage] is passed as `Any` to avoid the main module
     * needing a stable reference to its inner-class form across versions.
     * Implementations cast it to
     * `CompilerPluginRegistrar.ExtensionStorage` internally.
     *
     * @param extensionStorage actually a
     *   `CompilerPluginRegistrar.ExtensionStorage` instance (the receiver of
     *   `registerExtensions`)
     * @param configuration the `CompilerConfiguration` of the current compile
     * @param config the resolved [CaptureCodePluginConfig]
     * @param firRegistrar the plugin's [FirExtensionRegistrarAdapter] subclass
     *   instance to register
     * @param irExtension the plugin's [IrGenerationExtension] instance to register
     */
    public fun registerExtensions(
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        configuration: CompilerConfiguration,
        config: CaptureCodePluginConfig,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    )

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
