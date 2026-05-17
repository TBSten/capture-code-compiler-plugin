// task-120-B Phase 2: K2.4-RC で `putValueArgument` / `getValueArgument` /
// `putTypeArgument` / `getTypeArgument` / `valueParameters` / 旧 `IrCallImpl` ctor 等が
// 削除済 (deprecated ERROR を超えて symbol 自体消滅)。 内部 shim (`K240RcIrApiShims.kt`) を
// 経由するため deprecation 抑止は本 file では不要だが、 `deepCopyWithSymbols` 等は
// `@DeprecatedForRemovalCompilerApi` opt-in が要求されるため file header で対応する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k240rc.checker.K240RcCapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k240rc.checker.K240RcExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k240rc.checker.K240RcMarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k240rc.checker.K240RcMarkerCheckersExtension
import me.tbsten.capture.code.compat.k240rc.checker.K240RcRendererMapShim
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
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
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
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

/**
 * task-124 / task-119 follow-up: module-scoped singleton。 checker dispatcher は
 * 本 singleton を共有する。 [CompatContextImpl.Factory.create] は新規 instance を返す。
 */
internal val k240RcCompat: CompatContext = CompatContextImpl()

/**
 * Kotlin 2.4.0-RC 向けの [CompatContext] 実装。
 *
 * task-076: compat-k230 (Kotlin 2.3.x baseline) を template に mechanical copy + rename
 * したもの。 2.3.x → 2.4.0-RC の追加 native API drift は本 module で吸収する。 現時点で
 * 確認している方針:
 *
 * - **FIR checker `check()` signature**: 2.2.x 以降の `context(...) fun check(D)`
 *   (Java shim 経由) は 2.4.0-RC でもそのまま有効と想定。
 * - **IR API drift**: `IrMemberAccessExpression.putValueArgument` 等の deprecated migration
 *   は 2.4.0-RC でも継承。 大きな drift が観測されたら個別 ticket で対応。
 * - **Factory.minVersion = "2.4.0-RC"**: [KotlinToolingVersion] の pre-release tier
 *   (Maturity.RC) を介して比較される。 consumer kotlin = 2.4.0-RC の時に
 *   compat-k230 (minVersion 2.3.0, STABLE) より上位として選ばれる。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 */
public class CompatContextImpl : CompatContext {

    // task-120-B Phase 5: `transformIr(...)` SPI method removed. IR orchestration is
    // owned by `CaptureCodeIrExtension` (main module). `K240RcIrTransform` +
    // `K240RcCapturedSourcesCollector` + `K240RcCapturedSourcesRewriter` are now dead code
    // awaiting removal in Phase 6.

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.4.0-RC: FirLiteralExpression は型パラメータ無し (2.0.21+ と同形)。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    override fun containingFilePathOf(context: CheckerContext): String? =
        // Kotlin 2.4.x: `CheckerContext.containingFile` → `containingFilePath` (drift D12, 2.3.x 由来)。
        context.containingFilePath

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
        ::K240RcMarkerCheckersExtension,
        ::K240RcMarkerAnnotationCheckersExtension,
        ::K240RcCapturedSourcesCallCheckersExtension,
        ::K240RcExpressionAnnotationCheckersExtension,
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

    override fun diagnosticFactory(id: String): Any? = K240RcDiagnostics.MAP[id]

    // -- task-120-B Phase 2: IR primitive overrides (K2.4-RC baseline) --
    //
    // K2.4-RC で 削除されている旧 API:
    //  - `IrMemberAccessExpression.putValueArgument(Int, IrExpression?)` → `arguments[i] = value`
    //  - `IrMemberAccessExpression.getValueArgument(Int)` → `arguments[i]`
    //  - `IrMemberAccessExpression.putTypeArgument(Int, IrType?)` → `typeArguments[i] = type`
    //  - `IrMemberAccessExpression.getTypeArgument(Int)` → `typeArguments[i]`
    //  - `IrFunction.valueParameters` → `IrFunction.nonDispatchParameters` (IrUtilsKt extension)
    //
    // `K240RcIrApiShims.kt` で `putArgumentSafe` / `getArgumentSafe` / `setTypeArgumentSafe` /
    // `getTypeArgumentSafe` を提供しているので、 SPI override はそれら shim を呼ぶ。
    //
    // `IrCallImpl(...)` factory は K2.1+ の top-level 5 引数 form がそのまま残存。
    // `IrConstructorCallImpl.fromSymbolOwner` も top-level extension form を import。

    override fun walkIrTree(
        moduleFragment: IrModuleFragment,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    ) {
        moduleFragment.acceptChildrenVoid(
            K240RcCallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
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
            K240RcCallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
        )
    }

    override fun transformCallsInModule(
        moduleFragment: IrModuleFragment,
        onCall: (IrCall) -> IrExpression?,
    ) {
        moduleFragment.transformChildrenVoid(K240RcCallTransformer(onCall))
    }

    override fun putCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
        value: IrExpression?,
    ) {
        // K2.4-RC: `arguments[index] = value` via shim (null padding when needed).
        call.putArgumentSafe(index, value)
    }

    override fun getCallValueArgument(
        call: IrFunctionAccessExpression,
        index: Int,
    ): IrExpression? = call.getArgumentSafe(index)

    override fun setCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
        type: IrType?,
    ) {
        call.setTypeArgumentSafe(index, type)
    }

    override fun getCallTypeArgument(
        call: IrMemberAccessExpression<*>,
        index: Int,
    ): IrType? = call.getTypeArgumentSafe(index)

    override fun valueParametersOf(function: IrFunction): List<IrValueParameter> =
        // K2.4-RC: `valueParameters` is removed. `nonDispatchParameters` returns the
        // regular + context + extension-receiver parameters in declaration order.
        function.nonDispatchParameters

    override fun newIrCall(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        symbol: IrSimpleFunctionSymbol,
        typeArgumentsCount: Int,
    ): IrCall = IrCallImpl(
        // K2.1+ top-level factory form, still available on K2.4-RC.
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
        // K2.4-RC: `deepCopyWithSymbols` is `@DeprecatedForRemovalCompilerApi` but still
        // usable when opted-in (see file header `@OptIn`). Phase 2 keeps the existing
        // call shape; the replacement helper (when it stabilises) will be wired here
        // without changing the SPI surface.
        expression.deepCopyWithSymbols()

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "2.4.0-RC"

        override fun create(): CompatContext = CompatContextImpl()
    }

    /**
     * Kotlin 2.4.0-RC baseline 向けの **診断 factory** SSoT (task-121 で
     * 旧 `checker/K240RcCaptureCodeDiagnostics.kt` から本 nested object に集約)。
     *
     * 構造は [K230Diagnostics][me.tbsten.capture.code.compat.k230.CompatContextImpl.K230Diagnostics]
     * と同形。 K2.3+ の `KtDiagnosticsContainer` 継承 + lazy renderer MAP pattern を踏襲。
     */
    public object K240RcDiagnostics : KtDiagnosticsContainer() {

        // task-091: visibility / retention / target の 3 factory は撤廃。
        public val CC_MARKER_PARAMETER_TYPE_INVALID: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

        public val CC_MARKER_FILLER_REQUIRES_DEFAULT: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

        public val CC_MARKER_IS_EXPECT: KtDiagnosticFactory0 by error0<PsiElement>()

        public val CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE",
                severity = Severity.ERROR,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K240RcCaptureCodeDefaultMessages,
            )

        // task-123: warning factories (Severity.WARNING)。 K2.4-RC でも K2.3 と同様、
        // `KtDiagnosticFactory1` ctor に `rendererFactory` parameter が必須。

        public val CC_CAPTUREDSOURCES_NO_MARKER_FOUND: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_NO_MARKER_FOUND",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K240RcCaptureCodeDefaultMessages,
            )

        public val CC_MARKER_OVERRIDE_NO_EFFECT: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_OVERRIDE_NO_EFFECT",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K240RcCaptureCodeDefaultMessages,
            )

        public val CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K240RcCaptureCodeDefaultMessages,
            )

        public val CC_MARKER_PARAMETER_UNUSED: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_PARAMETER_UNUSED",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K240RcCaptureCodeDefaultMessages,
            )

        override fun getRendererFactory(): BaseDiagnosticRendererFactory = K240RcCaptureCodeDefaultMessages

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

        private object K240RcCaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
            // task-088: K230 と同じ理由で `by lazy` 化 (static init 循環依存の回避)。
            // 詳細は K230Diagnostics の comment 参照。
            override val MAP: KtDiagnosticFactoryToRendererMap by lazy {
                K240RcRendererMapShim.create("CaptureCode").apply {
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
}
