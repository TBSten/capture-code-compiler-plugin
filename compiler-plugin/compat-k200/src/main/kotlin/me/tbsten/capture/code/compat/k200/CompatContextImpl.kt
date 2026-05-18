package me.tbsten.capture.code.compat.k200

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k200.checker.FullyExpandedTypeShim
import me.tbsten.capture.code.compat.k200.checker.K200CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerCheckersExtension
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.CapturedSourcesCallErrors
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.CapturedSourcesWarnings
import me.tbsten.capture.code.feature.markerDefinition.MarkerDefinitionWarnings
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.MarkerAnnotationErrors
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.MarkerAnnotationWarnings
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.PsiIrFileEntry
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
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

/**
 * task-124 / task-119 follow-up: module-scoped singleton。 checker は本 singleton を共有する。
 * [CompatContextImpl.Factory.create] は ServiceLoader 契約上、 新規 instance を返す。
 */
internal val k200Compat: CompatContext = CompatContextImpl()

/**
 * Kotlin 2.0.x 向けの [CompatContext] 実装。
 *
 * 単一の Metro pattern interface (`CompatContext`) に統合された結果、 IR 変換と FIR
 * 補助メソッドの両方を本 class が実装する。
 *
 * - [transformIr] は [runK200IrTransform] (top-level 関数) に委譲。
 * - [literalValueOrNull] / [isLiteralExpression] は 2.0.0 ベースの
 *   `FirLiteralExpression<*>` (型パラメータ付き) を dispatch して `value` を読み出す。
 *   Kotlin 2.0.21+ では型パラメータが削除されているため、 同等処理を [k210][CompatContext]
 *   実装側で 2.1.0 native API で書き直す。
 * - [toRegularClassSymbolOrNull] は `org.jetbrains.kotlin.fir.types.toRegularClassSymbol`
 *   (Kotlin 2.0.x の package パス) を import して dispatch する。
 *
 * task-121: diagnostic factory 群は本 class の nested object [K200Diagnostics] に集約された。
 * 旧 `checker/K200CaptureCodeDiagnostics.kt` は削除済。 K200 checker は
 * [K200Diagnostics] の public field を直接参照する形に切替え、 cross-version dispatch は
 * [diagnosticFactory] 経由で行う。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 */
public class CompatContextImpl : CompatContext {

    // task-120-B Phase 5: `transformIr(...)` SPI method removed. IR orchestration is
    // owned by `CaptureCodeIrExtension` (main module) which drives main-side
    // `CollectDeclarationSite` + `RewriteCapturedSourcesCall` directly through the
    // 11 IR primitive overrides below. `K200IrTransform` + `K200CapturedSourcesCollector` +
    // `K200CapturedSourcesRewriter` are now dead code awaiting removal in Phase 6.

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.0.x の FirLiteralExpression は `<T>` 型パラメータ付き。
        // 2.0.21+ では型パラメータが削除されているため、 ここでは 2.0.0 native の `<*>`
        // を素直に dispatch し、 後続バージョンは別 compat module 側で再実装する。
        return (expression as? FirLiteralExpression<*>)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression<*>

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    // -- task-0.2.0-cifix: FIR type API drift dispatchers (K2.0 baseline) --
    //
    // K2.0.0 baseline では `FirTypeRef.coneType`, `FirTypeRef.coneTypeOrNull`,
    // `FirExpression.resolvedType`, `ConeKotlinType.classId` がいずれも
    // `org.jetbrains.kotlin.fir.types` package の extension として存在し、
    // 同名 / 同 signature で呼べる。 main module の bytecode が drift する
    // `FirResolvedTypeRef.getType()` 等の interface method shape を、
    // 各 compat-kXXX module が自身の baseline で再 link することで吸収する。

    override fun coneTypeOrNullOf(typeRef: FirTypeRef): ConeKotlinType? = typeRef.coneTypeOrNull

    override fun coneTypeOrErrorOf(typeRef: FirTypeRef): ConeKotlinType = typeRef.coneType

    override fun resolvedTypeOrNullOf(expression: FirExpression): ConeKotlinType? =
        // K2.0 では `resolvedType` は non-null を返す extension。 main 側で `?.` 連鎖したいので
        // safe-cast 経由で nullable 化。 expression 未解決 case は extension が
        // `ConeErrorType` を返す形なので null にはならないが、 main 側 API は nullable で揃える。
        expression.resolvedType

    override fun classIdOfType(type: ConeKotlinType): ClassId? = type.classId

    override fun containingFilePathOf(context: CheckerContext): String? =
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        // task-080: 2.0.x の `fullyExpandedType(session)` overload は 2.0.20 で削除されたため、
        // reflection shim 経由で 2.0.0 baseline / 2.0.20+ 両方を吸収する。
        FullyExpandedTypeShim.expand(type, session)

    /**
     * Logic C (design §5.C) のソーステキスト取得本体。 2 系統の fallback を持つ:
     * 1. PSI 経由 ([PsiIrFileEntry.psiFile.text]): offset 整合が確実。
     * 2. filesystem 経由: PSI が無い (LightTree / `NaiveSourceBasedFileEntryImpl` 等) 場合は
     *    [IrFile.fileEntry] の `name` (絶対パス) から `File.readText()`。 design §7.3 / R3。
     */
    override fun loadFileText(file: IrFile): String? {
        (file.fileEntry as? PsiIrFileEntry)?.psiFile?.text?.let { return it }
        val candidate = File(file.fileEntry.name)
        if (!candidate.isFile) return null
        return runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
    }

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K200MarkerCheckersExtension,
        ::K200MarkerAnnotationCheckersExtension,
        ::K200CapturedSourcesCallCheckersExtension,
        ::K200ExpressionAnnotationCheckersExtension,
    )

    // task-078: FIR / IR 拡張の登録は CompilerPluginRegistrar.ExtensionStorage の
    // `registerExtension(<Descriptor>, T)` を呼ぶが、 その descriptor の super class
    // (ProjectExtensionDescriptor vs ExtensionPointDescriptor) と
    // ExtensionStorage 側の引数型が Kotlin 2.3.0 で drift している。
    // 本 module は 2.0.0 native API でビルドされるため、 ここでの call site は
    // 2.0.x の ProjectExtensionDescriptor シグネチャに正しくリンクされる。
    override fun registerExtensions(
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        configuration: CompilerConfiguration,
        config: Any,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    ) {
        // task-120-B Phase 1: SPI `config` is Any-erased; the cast keeps the field
        // typed for any later wiring that wants to inspect it.
        @Suppress("UNUSED_VARIABLE")
        val typedConfig = config as CaptureCodePluginConfig
        with(extensionStorage) {
            FirExtensionRegistrarAdapter.registerExtension(firRegistrar)
            IrGenerationExtension.registerExtension(irExtension)
        }
    }

    override fun diagnosticFactory(id: String): Any? = K200Diagnostics.MAP[id]

    // -- task-120-B Phase 2: IR primitive overrides (K2.0 baseline) --
    //
    // K2.0 baseline では旧来 IR API がそのまま使えるため、 各 override は薄い 1 行 wrapper。
    // K2.0 → K2.1 で internal 化された `IrCallImpl(...)` 6 引数 ctor も本 module からは
    // 直接呼べる (compat-k200 が kotlin-compiler-embeddable 2.0.0 baseline で compile される)。

    override fun walkIrTree(
        moduleFragment: IrModuleFragment,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    ) {
        moduleFragment.acceptChildrenVoid(
            K200CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
        )
    }

    override fun walkIrFileDeclarations(
        file: IrFile,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    ) {
        file.acceptChildrenVoid(
            K200CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
        )
    }

    override fun transformCallsInModule(
        moduleFragment: IrModuleFragment,
        onCall: (IrCall) -> IrExpression?,
    ) {
        moduleFragment.transformChildrenVoid(K200CallTransformer(onCall))
    }

    override fun putCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
        value: IrExpression?,
    ) {
        call.putValueArgument(index, value)
    }

    override fun getCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
    ): IrExpression? = call.getValueArgument(index)

    override fun setCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
        type: IrType?,
    ) {
        call.putTypeArgument(index, type)
    }

    override fun getCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
    ): IrType? = call.getTypeArgument(index)

    override fun valueParametersOf(function: IrFunction): List<IrValueParameter> =
        function.valueParameters

    override fun newIrCall(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        symbol: IrSimpleFunctionSymbol,
        typeArgumentsCount: Int,
    ): IrCall = IrCallImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = type,
        symbol = symbol,
        typeArgumentsCount = typeArgumentsCount,
        // K2.0 / K2.0.21 baseline: `valueArgumentsCount` is part of the ctor signature.
        // Phase 2 callers (BuildMarkerInstance / RewriteCapturedSourcesCall) only build
        // `listOf(vararg)` calls, which always have exactly one value slot.
        valueArgumentsCount = 1,
    )

    override fun newIrConstructorCall(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        constructorSymbol: IrConstructorSymbol,
    ): IrConstructorCall = IrConstructorCallImpl.fromSymbolOwner(
        startOffset = startOffset,
        endOffset = endOffset,
        type = type,
        constructorSymbol = constructorSymbol,
    )

    override fun deepCopyExpression(expression: IrExpression): IrExpression =
        expression.deepCopyWithSymbols()

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "2.0.0"

        override fun create(): CompatContext = CompatContextImpl()
    }

    /**
     * Kotlin 2.0.x baseline 向けの **診断 factory** SSoT (task-121 で
     * 旧 `checker/K200CaptureCodeDiagnostics.kt` から本 nested object に集約)。
     *
     * ## なぜ各 compat-kXXX で独立宣言が必要か
     *
     * Kotlin 2.0.0 → 2.2.x 間で diagnostic API に以下の drift がある:
     *
     * - `KtDiagnosticFactory0` constructor が 4 引数 → 5 引数 (`BaseDiagnosticRendererFactory` 追加)
     * - `error0()` / `error1()` delegate provider のシグネチャ変更 (`KtDiagnosticsContainer` 追加)
     * - `RootDiagnosticRendererFactory` の API 変更
     *
     * main module を 2.0.0 baseline で compile すると `NoSuchMethodError` が 2.2.x runtime で発生する。
     * 各 compat-kXXX module が **自身の baseline で diagnostic factory を再宣言する** ことで
     * runtime drift を吸収する。
     *
     * 文面は main module の `MarkerAnnotationErrors` / `CapturedSourcesCallErrors` (English-only
     * SSoT, task-122 で `CaptureCodeDiagnosticMessages` から完全移管) を参照する。
     */
    public object K200Diagnostics {

        /** `CC_MARKER_PARAMETER_TYPE_INVALID` — Kotlin annotation 制約外の parameter 型。 */
        public val CC_MARKER_PARAMETER_TYPE_INVALID: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

        /** `CC_MARKER_FILLER_REQUIRES_DEFAULT` — filler 型 parameter にデフォルト値がない。 */
        public val CC_MARKER_FILLER_REQUIRES_DEFAULT: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

        /** `CC_MARKER_IS_EXPECT` — marker 自身が `expect` 宣言。 */
        public val CC_MARKER_IS_EXPECT: KtDiagnosticFactory0 by error0<PsiElement>()

        /** `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — T が `@CaptureCode` 付き marker ではない。 */
        public val CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE",
                severity = Severity.ERROR,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        // task-123: warning factories. severity = Severity.WARNING で直接構築する形に統一する
        // (`warning0` / `warning1` delegate も使えるが、 k230+ で API drift があるため
        // direct construction の方が compat 層を跨いで pattern を揃えやすい)。

        /** `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` — capturedSources<T>() の site が 0 件 (silent fail)。 */
        public val CC_CAPTUREDSOURCES_NO_MARKER_FOUND: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_NO_MARKER_FOUND",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        /** `CC_MARKER_OVERRIDE_NO_EFFECT` — `@CaptureCode(...)` override が global config と同値。 */
        public val CC_MARKER_OVERRIDE_NO_EFFECT: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_OVERRIDE_NO_EFFECT",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        /** `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` — 同 FQN marker が複数 module 由来で出現。 */
        public val CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        /** `CC_MARKER_PARAMETER_UNUSED` — default 値あり parameter が 1 度も override されない。 */
        public val CC_MARKER_PARAMETER_UNUSED: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_PARAMETER_UNUSED",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        /**
         * task-121: lazy MAP で id → factory を解決する。 task-088 の教訓
         * (静的初期化循環依存) を予防するため、 K2.3+ と同様に `by lazy` を採用。
         */
        internal val MAP: Map<String, Any> by lazy {
            mapOf(
                "CC_MARKER_PARAMETER_TYPE_INVALID" to CC_MARKER_PARAMETER_TYPE_INVALID,
                "CC_MARKER_FILLER_REQUIRES_DEFAULT" to CC_MARKER_FILLER_REQUIRES_DEFAULT,
                "CC_MARKER_IS_EXPECT" to CC_MARKER_IS_EXPECT,
                "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE" to CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                // task-123: warning factories
                "CC_CAPTUREDSOURCES_NO_MARKER_FOUND" to CC_CAPTUREDSOURCES_NO_MARKER_FOUND,
                "CC_MARKER_OVERRIDE_NO_EFFECT" to CC_MARKER_OVERRIDE_NO_EFFECT,
                "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN" to CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN,
                "CC_MARKER_PARAMETER_UNUSED" to CC_MARKER_PARAMETER_UNUSED,
            )
        }

        init {
            RootDiagnosticRendererFactory.registerFactory(K200CaptureCodeDefaultMessages)
        }

        /**
         * Renderer factory: ID → メッセージ。 文面は main module の English-only SSoT
         * (`MarkerAnnotationErrors` / `CapturedSourcesCallErrors`, task-122) を参照する。
         */
        private object K200CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
            override val MAP: KtDiagnosticFactoryToRendererMap =
                KtDiagnosticFactoryToRendererMap("CaptureCode").apply {
                    put(
                        CC_MARKER_PARAMETER_TYPE_INVALID,
                        MarkerAnnotationErrors.PARAMETER_TYPE_INVALID.message,
                        KtDiagnosticRenderers.TO_STRING,
                    )
                    put(
                        CC_MARKER_FILLER_REQUIRES_DEFAULT,
                        MarkerAnnotationErrors.FILLER_REQUIRES_DEFAULT.message,
                        KtDiagnosticRenderers.TO_STRING,
                    )
                    put(
                        CC_MARKER_IS_EXPECT,
                        MarkerAnnotationErrors.IS_EXPECT.message,
                    )
                    put(
                        CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                        CapturedSourcesCallErrors.T_NOT_CAPTURE_CODE.message,
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                    // task-123: warning factory renderers. 文面 SSoT は main 側 `*Warnings.kt`。
                    put(
                        CC_CAPTUREDSOURCES_NO_MARKER_FOUND,
                        CapturedSourcesWarnings.NO_MARKER_FOUND.message,
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                    put(
                        CC_MARKER_OVERRIDE_NO_EFFECT,
                        MarkerAnnotationWarnings.OVERRIDE_NO_EFFECT.message,
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                    put(
                        CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN,
                        MarkerDefinitionWarnings.DUPLICATE_MARKER_FQN.message,
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                    put(
                        CC_MARKER_PARAMETER_UNUSED,
                        MarkerDefinitionWarnings.PARAMETER_UNUSED.message,
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                }
        }
    }
}
