package me.tbsten.capture.code.compat

import java.util.ServiceLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
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
 *
 * ## task-120-B Phase 5 update (2026-05-17)
 *
 * The IR orchestration entry point `transformIr(moduleFragment, pluginContext, config)`
 * has been **removed**. `CaptureCodeIrExtension` now drives the IR phase end-to-end
 * via main-side [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite]
 * + [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall]
 * directly. Compat-kXXX modules only contribute IR primitives (`walkIrFileDeclarations`
 * / `transformCallsInModule` / `newIrCall` / `newIrConstructorCall` /
 * `putCallValueArgument` / `getCallValueArgument` / `setCallTypeArgument` /
 * `getCallTypeArgument` / `valueParametersOf` / `deepCopyExpression` / `walkIrTree`
 * / `loadFileText`) plus FIR checker wiring and diagnostic factory dispatch.
 */
public interface CompatContext {

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
     * Returns the [ConeKotlinType] of [typeRef], or `null` if it is not yet
     * resolved (or resolves to an error type).
     *
     * Absorbs drift D13: `FirTypeRef.coneTypeOrNull` extension delegates to
     * `(this as? FirResolvedTypeRef)?.type`, and the Java bytecode shape of
     * `FirResolvedTypeRef.type` (a Kotlin property whose getter
     * `FirResolvedTypeRef.getType()` is the actual JVM method symbol) changed
     * between K2.0 / K2.1 / K2.2 / K2.3 / K2.4-RC. Bytecode compiled against
     * the K2.0 baseline emits an `INVOKEINTERFACE` to
     * `FirResolvedTypeRef.getType()` whose return type / abstract-method
     * marker differs on later baselines, yielding `NoSuchMethodError` at
     * runtime when the plugin is loaded under K2.1+ / K2.2+ runtimes.
     *
     * Each compat-kXXX uses the `coneTypeOrNull` extension that exists on its
     * own kotlin-compiler-embeddable.
     *
     * Added in task-0.2.0-cifix (2026-05-18).
     */
    public fun coneTypeOrNullOf(typeRef: FirTypeRef): ConeKotlinType?

    /**
     * Returns the [ConeKotlinType] of [typeRef], or an error type if the
     * type ref is not yet resolved. Equivalent to the legacy
     * `FirTypeRef.coneType` extension (non-null variant of [coneTypeOrNullOf]).
     *
     * Absorbs the same drift D13 as [coneTypeOrNullOf]; main-module call
     * sites that previously read `typeRef.coneType` should route here instead.
     *
     * Added in task-0.2.0-cifix (2026-05-18).
     */
    public fun coneTypeOrErrorOf(typeRef: FirTypeRef): ConeKotlinType

    /**
     * Returns the resolved [ConeKotlinType] of [expression], or `null` if the
     * expression has not yet been resolved (or resolves to an error type).
     *
     * Absorbs drift D13 (same root cause as [coneTypeOrNullOf]) for
     * `FirExpression.resolvedType`, which internally calls
     * `(this.coneTypeOrNull ?: ConeErrorType(...))` and re-emerges as an
     * `INVOKEINTERFACE` to `FirResolvedTypeRef.getType()` at the bytecode
     * level.
     *
     * Each compat-kXXX uses the `resolvedType` extension that exists on its
     * own kotlin-compiler-embeddable.
     *
     * Added in task-0.2.0-cifix (2026-05-18).
     */
    public fun resolvedTypeOrNullOf(expression: FirExpression): ConeKotlinType?

    /**
     * Returns the [ClassId] of [type], or `null` if [type] is a non-class
     * type (type parameter / star projection / error type / etc.).
     *
     * Absorbs drift D14: the `ConeKotlinType.classId` extension was
     * stable in API surface across K2.0-K2.4-RC, but its underlying
     * implementation delegates to `(type.lookupTagOrNull as? ConeClassLikeLookupTag)?.classId`,
     * where `ConeClassLikeLookupTag.classId`'s Kotlin property getter
     * (`ConeClassLikeLookupTag.getClassId()`) shifted across baselines.
     * Bytecode compiled against the K2.0 baseline references the K2.0
     * abstract-method shape, which is no longer dispatched on K2.2+
     * runtimes → `NoSuchMethodError`.
     *
     * Distinct from [classIdOf]: that overload takes a
     * `FirRegularClassSymbol` and reads `symbol.classId` directly; this one
     * takes a `ConeKotlinType` and reads `type.classId`.
     *
     * Added in task-0.2.0-cifix (2026-05-18).
     */
    public fun classIdOfType(type: ConeKotlinType): ClassId?

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
     * task-120-B Phase 1: the `config` parameter is **temporarily `Any`-erased**
     * to keep the compat module free of plugin-domain types
     * (`me.tbsten.capture.code.CaptureCodePluginConfig` was hoisted to the
     * main module). Each compat-kXXX impl casts back to
     * `CaptureCodePluginConfig` internally. Phase 4 narrows this surface again
     * once the plugin-domain config is no longer threaded through the SPI.
     *
     * @param extensionStorage actually a
     *   `CompilerPluginRegistrar.ExtensionStorage` instance (the receiver of
     *   `registerExtensions`)
     * @param configuration the `CompilerConfiguration` of the current compile
     * @param config the resolved plugin config (`CaptureCodePluginConfig` in main)
     * @param firRegistrar the plugin's [FirExtensionRegistrarAdapter] subclass
     *   instance to register
     * @param irExtension the plugin's [IrGenerationExtension] instance to register
     */
    public fun registerExtensions(
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        configuration: CompilerConfiguration,
        config: Any,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    )

    /**
     * Returns the `KtDiagnosticFactory0` / `KtDiagnosticFactory1<*>` instance
     * registered for the given diagnostic [id], or `null` if this compat
     * implementation does not have one.
     *
     * The returned value is intentionally typed as `Any?` because
     * `KtDiagnosticFactory0` vs `KtDiagnosticFactory1<*>` cannot be expressed
     * by a single covariant return type, and the main module's
     * `error/ReportError.kt` / `warning/ReportWarning.kt` helpers already
     * narrow the result via `as? KtDiagnosticFactory0` / `as? KtDiagnosticFactory1<...>`
     * before calling `reporter.reportOn(...)`.
     *
     * Each `compat-kXXX/CompatContextImpl.kt` looks the id up in a
     * `private object K{XXX}Diagnostics` nested inside the impl, so the
     * factory map is co-located with the implementation that owns it. The id
     * convention is `CC_<feature>_<rule>` (see each feature's
     * `*Errors.kt` SSoT, e.g. `MarkerAnnotationErrors.kt`).
     *
     * Added in task-121.
     */
    public fun diagnosticFactory(id: String): Any?

    // -- task-120-B Phase 2: IR primitive method group --
    //
    // The 11 methods below absorb IR phase drift so the main module's logic
    // classes can stay on the Kotlin 2.0 baseline while each compat-kXXX
    // dispatches to the IR API shape that exists on its own
    // kotlin-compiler-embeddable. Drift identifiers (D-IR-X) cross-reference
    // `.local/tmp/task-120-B-phase0-spi-design.md` §1 / §2.
    //
    // Each method is currently a placeholder: the main-side logic classes
    // remain UnsupportedOperationException until Phase 3+ migrates them into
    // main. Phase 2's responsibility is solely to land the SPI surface and
    // verify all compat-kXXX modules compile against their own baseline.

    /**
     * Walks every declaration in [moduleFragment] and dispatches to the
     * matching callback for class / simple-function / property / type-alias
     * declarations. Nested declarations are visited recursively.
     *
     * Absorbs drift D-IR-1: the IR visitor base type changed from interface
     * `IrElementVisitorVoid` (K2.0-K2.1) to class `IrVisitorVoid` (K2.2+).
     * Each compat-kXXX uses a private visitor that derives from the type
     * available on its own baseline and forwards into the callbacks.
     *
     * File-level annotations are **not** delivered here — visitor recursion
     * does not enter `IrFile.annotations`. Use [walkIrFileDeclarations]
     * combined with main-side iteration over `moduleFragment.files` if file
     * annotations are needed.
     */
    public fun walkIrTree(
        moduleFragment: IrModuleFragment,
        onClass: (IrClass) -> Unit = {},
        onSimpleFunction: (IrSimpleFunction) -> Unit = {},
        onProperty: (IrProperty) -> Unit = {},
        onTypeAlias: (IrTypeAlias) -> Unit = {},
    )

    /**
     * Walks declarations within a single [file] and dispatches to the matching
     * callback. The four element kinds correspond to the marker capture site
     * types (`CapturedSite.CaptureKind.{CLASS, FUNCTION, PROPERTY, TYPEALIAS}`).
     *
     * Same drift absorbed as [walkIrTree] (D-IR-1). The split exists so the
     * main-side collector can interleave per-file work (such as
     * `collectFileAnnotations` and `collectExpressionSites`) with the visitor
     * pass without re-walking the whole module.
     */
    public fun walkIrFileDeclarations(
        file: IrFile,
        onClass: (IrClass) -> Unit = {},
        onSimpleFunction: (IrSimpleFunction) -> Unit = {},
        onProperty: (IrProperty) -> Unit = {},
        onTypeAlias: (IrTypeAlias) -> Unit = {},
    )

    /**
     * Transforms every `IrCall` in [moduleFragment] via [onCall]. A `null`
     * return from the callback leaves the original call untouched; a non-null
     * `IrExpression` replaces it. Children are visited before the callback so
     * nested rewrites compose correctly.
     *
     * Absorbs drift D-IR-1/D-IR-2 by hiding the transformer base type
     * (`IrElementTransformerVoid` is stable across baselines but is reached
     * through SPI for symmetry with [walkIrTree]).
     *
     * Used by `RewriteCapturedSourcesCall` to replace
     * `capturedSources<T>()` calls with `listOf(T(...))`.
     */
    public fun transformCallsInModule(
        moduleFragment: IrModuleFragment,
        onCall: (IrCall) -> IrExpression?,
    )

    /**
     * Sets the value argument at [index] on [call]. Equivalent to the legacy
     * `call.putValueArgument(index, value)` extension.
     *
     * Absorbs drift D-IR-5: `putValueArgument` was deprecated in K2.2+ and
     * **removed entirely in K2.4-RC**. The K2.4-RC implementation routes
     * through `arguments[index] = value` semantics (the `putArgumentSafe`
     * shim defined in `K240RcIrApiShims.kt` extends the underlying
     * `MutableList<IrExpression?>` with null-padding when needed).
     */
    public fun putCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
        value: IrExpression?,
    )

    /**
     * Returns the value argument at [index] on [call], or `null` if no
     * argument has been placed there. Equivalent to the legacy
     * `call.getValueArgument(index)` extension.
     *
     * Absorbs drift D-IR-7 (companion to [putCallValueArgument]).
     */
    public fun getCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
    ): IrExpression?

    /**
     * Sets the type argument at [index] on [call]. Equivalent to the legacy
     * `call.putTypeArgument(index, type)` extension.
     *
     * Absorbs drift D-IR-6: `putTypeArgument` was deprecated in K2.2+ and
     * removed in K2.4-RC. The K2.4-RC implementation routes through
     * `typeArguments[index] = type` via the `setTypeArgumentSafe` shim.
     */
    public fun setCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
        type: IrType?,
    )

    /**
     * Returns the type argument at [index] on [call], or `null` if no type
     * argument has been placed there. Equivalent to the legacy
     * `call.getTypeArgument(index)` extension.
     *
     * Absorbs drift D-IR-8 (companion to [setCallTypeArgument]).
     */
    public fun getCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
    ): IrType?

    /**
     * Returns the non-dispatch / non-extension value parameters of [function]
     * (= REGULAR + optional vararg parameter, in declaration order).
     *
     * Absorbs drift D-IR-3/D-IR-33: `IrFunction.valueParameters` was
     * deprecated in K2.3 and **removed in K2.4-RC**, replaced by
     * `nonDispatchParameters`. The K2.4-RC implementation returns
     * `function.nonDispatchParameters`; earlier baselines return
     * `function.valueParameters` (with `@Suppress("DEPRECATION")` from K2.3).
     */
    public fun valueParametersOf(function: IrFunction): List<IrValueParameter>

    /**
     * Builds an [IrCall] for the given [symbol]. The constructed call carries
     * `typeArgumentsCount = typeArgumentsCount` and pre-allocates exactly one
     * value argument slot (`valueArgumentsCount = 1`), which matches the
     * `listOf(vararg)` rewrite site used by `BuildMarkerInstance`.
     *
     * Absorbs drift D-IR-9: `IrCallImpl(...)` ctor signature changed:
     * - K2.0 / K2.0.21: 6-arg ctor (start, end, type, symbol,
     *   typeArgumentsCount, valueArgumentsCount).
     * - K2.1+: 5-arg top-level factory; `valueArgumentsCount` is inferred
     *   from `symbol.owner.valueParameters.size` so it is no longer passed
     *   explicitly.
     *
     * Implementations are free to ignore [typeArgumentsCount] if the IR API
     * on their baseline derives it automatically; the parameter is kept on
     * the signature so K2.0 / K2.0.21 baselines can pass it through.
     */
    public fun newIrCall(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        symbol: IrSimpleFunctionSymbol,
        typeArgumentsCount: Int,
    ): IrCall

    /**
     * Builds an [IrConstructorCall] for the given [constructorSymbol] via the
     * appropriate `fromSymbolOwner` factory on the current baseline.
     *
     * Absorbs drift D-IR-10: `IrConstructorCallImpl.fromSymbolOwner` was a
     * companion-object static on K2.0 / K2.0.21 and was hoisted to a
     * top-level extension on K2.1+ (`org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner`).
     * Each compat-kXXX imports / calls the form that exists on its baseline.
     */
    public fun newIrConstructorCall(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        constructorSymbol: IrConstructorSymbol,
    ): IrConstructorCall

    /**
     * Returns a deep copy of [expression] with re-bound IrSymbol nodes,
     * suitable for re-attaching to a different IR tree position without
     * violating IR parent-pointer invariants.
     *
     * Absorbs drift D-IR-15: `IrExpression.deepCopyWithSymbols()` is marked
     * `@DeprecatedForRemovalCompilerApi` in K2.4-RC. The K2.4-RC
     * implementation opts in via `@file:OptIn(DeprecatedForRemovalCompilerApi)`
     * and continues to invoke the same extension; once an alternative
     * lands in a later kotlin release this method will route through it
     * without changing the SPI surface.
     *
     * Used by `BuildUserArg` to clone a call-site argument expression
     * before re-attaching it to a freshly constructed marker
     * `IrConstructorCall`.
     */
    public fun deepCopyExpression(expression: IrExpression): IrExpression

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
