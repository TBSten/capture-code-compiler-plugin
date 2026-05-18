// task-120-B Phase 2: K2.3 でも旧 IR API (`putValueArgument` / `IrCallImpl` factory 等) は
// deprecated 化されているが、 compat-k230 module 自体が K2.3 baseline で compile される間は
// 引き続き呼べる。 deprecation warning を file 単位で抑止する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k230

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k230.checker.K230CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230RendererMapShim
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
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

/**
 * task-124 / task-119 follow-up: module-scoped singleton。 旧来は checker dispatcher 各々が
 * `CompatContextImpl()` を new していたが、 process 内で常に同一 SPI 実装を共有する形に統一。
 * [CompatContextImpl.Factory.create] は新規 instance を返す (ServiceLoader 契約のため) が、
 * checker 側 dispatcher / IR 側 helper はこの singleton を参照する。
 */
internal val k230Compat: CompatContext = CompatContextImpl()

/**
 * Kotlin 2.3.x 向けの [CompatContext] 実装。
 *
 * task-075: compat-k220 (Kotlin 2.2.x baseline) を template に mechanical copy + rename
 * したもの。 2.2.x → 2.3.x の追加 native API drift は本 module で吸収する。 現時点で
 * 確認している方針:
 *
 * - **FIR checker `check()` signature**: 2.2.x で導入された `context(...) fun check(D)`
 *   (Java shim 経由) は 2.3.x でもそのまま有効と想定。
 * - **IR API drift**: `IrMemberAccessExpression.putValueArgument` 等の deprecated migration
 *   は 2.3.x でも継承。 大きな drift が観測されたら個別 ticket で対応。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 */
public class CompatContextImpl : CompatContext {

    // task-120-B Phase 5: `transformIr(...)` SPI method removed. IR orchestration is
    // owned by `CaptureCodeIrExtension` (main module). `K230IrTransform` +
    // `K230CapturedSourcesCollector` + `K230CapturedSourcesRewriter` are now dead code
    // awaiting removal in Phase 6.

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.3.x: FirLiteralExpression は型パラメータ無し (2.0.21+ と同形)。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    // -- task-0.2.0-cifix: FIR type API drift dispatchers (K2.3 baseline) --
    //
    // 2.3.x baseline でも `FirTypeRef.coneType`, `FirTypeRef.coneTypeOrNull`,
    // `FirExpression.resolvedType`, `ConeKotlinType.classId` extension は引き続き
    // `org.jetbrains.kotlin.fir.types` package に同 signature で存在。 K2.3 で導入された
    // `containingFilePath` drift (D12) と同様、 本 SPI 経由で main bytecode の `getType()` /
    // `getClassId()` interface dispatch shape drift を吸収する。

    override fun coneTypeOrNullOf(typeRef: FirTypeRef): ConeKotlinType? = typeRef.coneTypeOrNull

    override fun coneTypeOrErrorOf(typeRef: FirTypeRef): ConeKotlinType = typeRef.coneType

    override fun resolvedTypeOrNullOf(expression: FirExpression): ConeKotlinType? =
        expression.resolvedType

    override fun classIdOfType(type: ConeKotlinType): ClassId? = type.classId

    override fun containingFilePathOf(context: CheckerContext): String? =
        // Kotlin 2.3.x: `CheckerContext.containingFile` accessor が削除され、
        // `containingFilePath: String?` に置き換わった (drift D12)。
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
        ::K230MarkerCheckersExtension,
        ::K230MarkerAnnotationCheckersExtension,
        ::K230CapturedSourcesCallCheckersExtension,
        ::K230ExpressionAnnotationCheckersExtension,
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

    override fun diagnosticFactory(id: String): Any? = K230Diagnostics.MAP[id]

    // -- task-120-B Phase 2: IR primitive overrides (K2.3 baseline) --
    //
    // K2.3 でも `IrCallImpl(...)` factory / `putValueArgument` 等 旧 API は引き続き
    // public available。 deprecation warning は file header の `@file:Suppress` で抑制。

    override fun walkIrTree(
        moduleFragment: IrModuleFragment,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    ) {
        moduleFragment.acceptChildrenVoid(
            K230CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
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
            K230CallbackVisitor(onClass, onSimpleFunction, onProperty, onTypeAlias),
        )
    }

    override fun transformCallsInModule(
        moduleFragment: IrModuleFragment,
        onCall: (IrCall) -> IrExpression?,
    ) {
        moduleFragment.transformChildrenVoid(K230CallTransformer(onCall))
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

    // -- task-0.2.0-cifix-ir: IR factory drift dispatchers (K2.3 baseline) --
    //
    // K2.3 baseline でも `IrVarargImpl(...)`, `IrConstImpl(...)`, `IrConstImpl.string(...)` /
    // `.int(...)`, `IrGetEnumValueImpl(...)` の top-level / companion builders は引き続き
    // public available。 deprecation warning は file header の `@file:Suppress` で抑制済。

    override fun newIrVararg(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        varargElementType: IrType,
        elements: List<IrVarargElement>,
    ): IrVararg = IrVarargImpl(
        startOffset = startOffset,
        endOffset = endOffset,
        type = type,
        varargElementType = varargElementType,
        elements = elements,
    )

    override fun newIrConstString(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        value: String,
    ): IrExpression = IrConstImpl.string(startOffset, endOffset, type, value)

    override fun newIrConstInt(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        value: Int,
    ): IrExpression = IrConstImpl.int(startOffset, endOffset, type, value)

    override fun newIrConstPrimitive(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        kind: Any,
        value: Any?,
    ): IrExpression = IrConstImpl(
        // K2.3 baseline: `IrConstKind` は K2.2 以来 non-generic。
        startOffset = startOffset,
        endOffset = endOffset,
        type = type,
        kind = kind as IrConstKind,
        value = value,
    )

    override fun newIrGetEnumValue(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        symbol: IrEnumEntrySymbol,
    ): IrExpression = IrGetEnumValueImpl(startOffset, endOffset, type, symbol)

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "2.3.0"

        override fun create(): CompatContext = CompatContextImpl()
    }

    /**
     * Kotlin 2.3.x baseline 向けの **診断 factory** SSoT (task-121 で
     * 旧 `checker/K230CaptureCodeDiagnostics.kt` から本 nested object に集約)。
     *
     * ## 2.2.x → 2.3.x の drift と吸収
     *
     * Kotlin 2.3.0 で diagnostic factory 周りに以下の breaking change が入った:
     *
     * 1. **`RootDiagnosticRendererFactory` の削除**: 旧 `RootDiagnosticRendererFactory.registerFactory(...)`
     *    によるグローバル登録 API は廃止された。 代わりに `KtDiagnosticsContainer` を継承した
     *    object 自体が `getRendererFactory()` で `BaseDiagnosticRendererFactory` を返す形になった。
     * 2. **`error0` / `error1` extension の receiver 変更**: これらは `KtDiagnosticsContainer.()` の
     *    extension に変更された (本 nested object が `KtDiagnosticsContainer` を継承しているため
     *    そのまま呼べる)。
     * 3. **`KtDiagnosticFactory1` constructor に `rendererFactory: BaseDiagnosticRendererFactory`
     *    parameter が追加**。
     * 4. **`KtDiagnosticFactoryToRendererMap` の constructor が public 化** された (旧 internal、
     *    ただし Kotlin metadata 上は internal のままなので [K230RendererMapShim] 経由で構築)。
     */
    public object K230Diagnostics : KtDiagnosticsContainer() {

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
                rendererFactory = K230CaptureCodeDefaultMessages,
            )

        // task-123: warning factories (Severity.WARNING)。 K2.3 では
        // `KtDiagnosticFactory1` ctor に `rendererFactory` parameter が必須。

        public val CC_CAPTUREDSOURCES_NO_MARKER_FOUND: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_NO_MARKER_FOUND",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K230CaptureCodeDefaultMessages,
            )

        public val CC_MARKER_OVERRIDE_NO_EFFECT: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_OVERRIDE_NO_EFFECT",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K230CaptureCodeDefaultMessages,
            )

        public val CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K230CaptureCodeDefaultMessages,
            )

        public val CC_MARKER_PARAMETER_UNUSED: KtDiagnosticFactory1<String> =
            KtDiagnosticFactory1(
                name = "CC_MARKER_PARAMETER_UNUSED",
                severity = Severity.WARNING,
                defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
                psiType = KtElement::class,
                rendererFactory = K230CaptureCodeDefaultMessages,
            )

        override fun getRendererFactory(): BaseDiagnosticRendererFactory = K230CaptureCodeDefaultMessages

        /**
         * task-121: lazy MAP (task-088 教訓に従い静的初期化循環依存を予防)。
         * `id -> factory` を解決するのに使われる。
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

        private object K230CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
            // task-075: 2.3.x で `KtDiagnosticFactoryToRendererMap(String)` constructor は
            // Kotlin metadata 上 `internal` のままなので Java shim 経由で構築する
            // (JVM bytecode 上は public)。
            //
            // task-088: **`by lazy` で MAP の初期化を遅延** させる。 eager (= `=` で
            // immediate init) だと、 outer `K230Diagnostics` の最初の `by error0` の
            // `provideDelegate` が `getRendererFactory()` を呼んで この inner object の
            // `<clinit>` がトリガーされた瞬間、 `put(CC_MARKER_*, ...)` が outer の
            // **まだ delegate が null の property** を参照して NPE する (static init の
            // 循環依存)。 `by lazy` ならば 1st access が outer init 完了後になるため
            // 安全。 報告された unstoppable consumer NPE の根本原因。
            override val MAP: KtDiagnosticFactoryToRendererMap by lazy {
                K230RendererMapShim.create("CaptureCode").apply {
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
