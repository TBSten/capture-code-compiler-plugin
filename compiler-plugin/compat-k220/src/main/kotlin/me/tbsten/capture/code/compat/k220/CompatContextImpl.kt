// task-120-B Phase 2: 旧 IR API (`putValueArgument` / `IrCallImpl` ctor 等) は K2.2+ で
// deprecated だが、 compat-k220 module 自体が K2.2 baseline で compile される間は引き続き
// public final method として呼べる。 deprecated warning を file 単位で抑止する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k220

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k220.checker.K220CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220MarkerCheckersExtension
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
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.resolvedType
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
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
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
 * task-124 / task-119 follow-up: module-scoped singleton。 checker dispatcher は
 * 本 singleton を共有する。 [CompatContextImpl.Factory.create] は新規 instance を返す。
 */
internal val k220Compat: CompatContext = CompatContextImpl()

/**
 * Kotlin 2.2.x 向けの [CompatContext] 実装。
 *
 * 2.1.x からは下記 drift を **追加で** 吸収する (FIR D1–D4 / IR D5–D7 は 2.1.x で吸収済の
 * パターンをそのまま継承):
 *
 * - **FIR checker `check()` signature drift**: 2.0 / 2.1 系では
 *   `FirDeclarationChecker.check(declaration, context, reporter)` だったのに対し、
 *   2.2.x からは `check(context, reporter)` (declaration は `CheckerContext` 経由で取れる)
 *   に変わっている。 ただし `FirDeclarationChecker<T>` の 2.2.x 抽象 method は依然
 *   declaration を渡す形を維持しているケースがあり、 patch によって差分が出ることが
 *   ある。 本 module の checker は **共通の compat checker (k210 由来) と同じ
 *   `check(declaration, context, reporter)` 形** に揃え、 2.2.x で signature drift が
 *   観測された patch では task-072 / task-074 で扱う方針に従う。
 * - **IR drift D8**: 2.2.x では `IrMemberAccessExpression.putValueArgument(int, IrExpression)`
 *   が deprecated / 削除候補に入り、 代わりに `arguments[i] = expr` (MutableList migration)
 *   が推奨される。 ただし 2.2.0 final 時点では旧 API も残存しているため、 既存 k210 と
 *   同じ `putValueArgument(...)` 呼び出しが compile / runtime とも通る。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 */
public class CompatContextImpl : CompatContext {

    // task-120-B Phase 5: `transformIr(...)` SPI method removed. IR orchestration is
    // owned by `CaptureCodeIrExtension` (main module). `K220IrTransform` +
    // `K220CapturedSourcesCollector` + `K220CapturedSourcesRewriter` are now dead code
    // awaiting removal in Phase 6.

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.2.x: FirLiteralExpression は型パラメータ無し (2.0.21+ と同形)。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    // -- task-0.2.0-cifix: FIR type API drift dispatchers (K2.2 baseline) --
    //
    // 2.2.x baseline でも `FirTypeRef.coneType`, `FirTypeRef.coneTypeOrNull`,
    // `FirExpression.resolvedType`, `ConeKotlinType.classId` extension は同形で残存。
    // ただし bytecode 上 `FirResolvedTypeRef.getType()` の interface dispatch shape が
    // K2.0 baseline と微妙に異なるため、 main module から直接呼ぶと NSME が発生する
    // (本 SPI が absorb する対象の drift)。

    override fun coneTypeOrNullOf(typeRef: FirTypeRef): ConeKotlinType? = typeRef.coneTypeOrNull

    override fun coneTypeOrErrorOf(typeRef: FirTypeRef): ConeKotlinType = typeRef.coneType

    override fun resolvedTypeOrNullOf(expression: FirExpression): ConeKotlinType? =
        expression.resolvedType

    override fun classIdOfType(type: ConeKotlinType): ClassId? = type.classId

    override fun containingFilePathOf(context: CheckerContext): String? =
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        type.fullyExpandedType(session)

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
        ::K220MarkerCheckersExtension,
        ::K220MarkerAnnotationCheckersExtension,
        ::K220CapturedSourcesCallCheckersExtension,
        ::K220ExpressionAnnotationCheckersExtension,
    )

    // task-078: 詳細は CompatContext.registerExtensions の KDoc 参照。
    override fun registerExtensions(
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        configuration: CompilerConfiguration,
        config: Any,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    ) {
        // task-120-B Phase 1: SPI Any-erased; the cast keeps the field typed.
        @Suppress("UNUSED_VARIABLE")
        val typedConfig = config as CaptureCodePluginConfig
        with(extensionStorage) {
            FirExtensionRegistrarAdapter.registerExtension(firRegistrar)
            IrGenerationExtension.registerExtension(irExtension)
        }
    }

    override fun diagnosticFactory(id: String): Any? = K220Diagnostics.MAP[id]

    // -- task-120-B Phase 2: IR primitive overrides (K2.2 baseline) --
    //
    // K2.2: `IrElementVisitorVoid` interface は **deprecated**、 `IrVisitorVoid` class が
    // 推奨だが旧 interface もまだ public available。 `putValueArgument` 等 extension は
    // 同じく deprecated だが call は通る。 `IrCallImpl(...)` factory も deprecated ですが
    // 互換 API として残存。 deprecation warning は file header の `@file:Suppress` で抑制。

    override fun walkIrTree(
        moduleFragment: IrModuleFragment,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    ) {
        moduleFragment.acceptChildrenVoid(
            K220CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
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
            K220CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
        )
    }

    override fun transformCallsInModule(
        moduleFragment: IrModuleFragment,
        onCall: (IrCall) -> IrExpression?,
    ) {
        moduleFragment.transformChildrenVoid(K220CallTransformer(onCall))
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
        override val minVersion: String = "2.2.0"

        override fun create(): CompatContext = CompatContextImpl()
    }

    /**
     * Kotlin 2.2.x baseline 向けの **診断 factory** SSoT (task-121 で
     * 旧 `checker/K220CaptureCodeDiagnostics.kt` から本 nested object に集約)。
     *
     * 構造は [K200Diagnostics][me.tbsten.capture.code.compat.k200.CompatContextImpl.K200Diagnostics]
     * と同形。 2.2.x は `KtDiagnosticFactory*` constructor / `error0`/`error1` delegate の
     * signature が 2.0/2.1 と互換のため 機械的コピーで OK。 詳細な背景は K200 側 KDoc 参照。
     */
    public object K220Diagnostics {

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

        // task-123: warning factories (Severity.WARNING で直接構築)。

        public val CC_CAPTUREDSOURCES_NO_MARKER_FOUND: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_NO_MARKER_FOUND",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        public val CC_MARKER_OVERRIDE_NO_EFFECT: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_OVERRIDE_NO_EFFECT",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        public val CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        public val CC_MARKER_PARAMETER_UNUSED: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_PARAMETER_UNUSED",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
            )

        /** task-121: lazy MAP (task-088 教訓に従い静的初期化循環依存を予防)。 */
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
            RootDiagnosticRendererFactory.registerFactory(K220CaptureCodeDefaultMessages)
        }

        private object K220CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
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
