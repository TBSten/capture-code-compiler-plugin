package me.tbsten.capture.code.compat.k202

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k202.checker.FullyExpandedTypeShim
import me.tbsten.capture.code.compat.k202.checker.K202CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202MarkerCheckersExtension
import me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.CapturedSourcesCallErrors
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.CapturedSourcesWarnings
import me.tbsten.capture.code.feature.markerDefinition.MarkerDefinitionWarnings
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.MarkerAnnotationErrors
import me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.MarkerAnnotationWarnings
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement

/**
 * task-124 / task-119 follow-up: module-scoped singleton。 checker は本 singleton を共有する。
 * [CompatContextImpl.Factory.create] は ServiceLoader 契約上、 新規 instance を返す。
 */
internal val k202Compat: CompatContext = CompatContextImpl()

/**
 * Kotlin 2.0.10 〜 2.0.21 (= 2.0.x patch level) 向けの [CompatContext] 実装。
 *
 * task-081: 2.0.20+ runtime と 2.0.0 baseline binary (compat-k200) との間で識別された
 * 多数の binary-incompat drift を、 個別 reflection shim ではなく「2.0.21 baseline で
 * compile された別 module」 として ServiceLoader 経由で dispatch することで吸収する。
 *
 * 主な drift (task-080 で 2.0.21 simulation 時に surface):
 *
 * - **FIR drift D1**: `FirLiteralExpression<T>` の型パラメータ削除。 ここでは 2.0.21 native の
 *   非ジェネリック `FirLiteralExpression` を直接 dispatch する。
 * - **IR drift**: `IrVarargImplKt` / `IrGetEnumValueImplKt` / `IrConstImplKt` の top-level
 *   factory が削除され、 class constructor のみが残った。 各 builder は class constructor を
 *   直接呼ぶ形に書き換える ([K202CapturedSourcesRewriter] /
 *   [me.tbsten.capture.code.compat.k202.userargs.UserArgPrimitiveIrBuilder] を参照)。
 * - **IR drift**: `IrCallImpl(..., valueArgumentsCount = ...)` constructor 廃止。 top-level
 *   factory function (`org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl(...)`) を経由し、
 *   `valueArgumentsCount` は symbol.owner.valueParameters.size から自動初期化される。
 * - **IR drift**: `IrConst<T>` / `IrConstKind<T>` の型パラ削除。 非ジェネリック `IrConst` を使う。
 * - **IR drift**: `DeepCopyTypeRemapper(SymbolRemapper)` ctor → `(ReferencedSymbolRemapper)`。
 *   `deepCopyWithSymbols()` extension は 2.0.21 native でそのまま呼べる (内部実装変更は吸収済)。
 *
 * `toRegularClassSymbol` extension は 2.0.21 では引き続き `org.jetbrains.kotlin.fir.types`
 * package に存在する (2.1.0 で `fir.resolve` に移動)。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 * `minVersion = "2.0.10"` を宣言し、 resolveFactory が 2.0.10 / 2.0.20 / 2.0.21 patch で
 * 本 module を自動選択する (compat-k200 minVersion 2.0.0 より新しいため)。
 */
public class CompatContextImpl : CompatContext {

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: Any,
    ) {
        // task-120-B Phase 1: SPI Any-erased; cast back to the main module type.
        runK202IrTransform(moduleFragment, pluginContext, config as CaptureCodePluginConfig)
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.0.21+ では FirLiteralExpression の `<T>` 型パラメータが削除されている
        // (task-080 解析 / FIR drift D1)。 2.0.21 native API では型パラ無しで dispatch する。
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
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        // task-080: 2.0.20+ では 2-arg `fullyExpandedType` overload が削除されたため、
        // reflection shim 経由で 3-arg overload を dispatch する。
        FullyExpandedTypeShim.expand(type, session)

    override fun loadFileText(file: IrFile): String? = SourceTextExtractor.loadFileText(file)

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K202MarkerCheckersExtension,
        ::K202MarkerAnnotationCheckersExtension,
        ::K202CapturedSourcesCallCheckersExtension,
        ::K202ExpressionAnnotationCheckersExtension,
    )

    // task-078: FIR / IR 拡張の登録は CompilerPluginRegistrar.ExtensionStorage の
    // `registerExtension(<Descriptor>, T)` を呼ぶが、 その descriptor の super class
    // (ProjectExtensionDescriptor vs ExtensionPointDescriptor) と
    // ExtensionStorage 側の引数型が Kotlin 2.3.0 で drift している。
    // 本 module は 2.0.21 native API でビルドされるため、 ここでの call site は
    // 2.0.21 の ProjectExtensionDescriptor シグネチャに正しくリンクされる。
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

    override fun diagnosticFactory(id: String): Any? = K202Diagnostics.MAP[id]

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        // task-081: 2.0.20 / 2.0.21 patch を本 module が dispatch する。
        //
        // resolveFactory は `<= currentVersion` の中で minVersion が最大のものを選ぶため、
        //   - 2.0.0  → compat-k200 (minVersion 2.0.0)
        //   - 2.0.10 → compat-k200 (minVersion 2.0.10 を試したが、 2.0.21 native binary が
        //              `org.jetbrains.kotlin.ir.expressions.impl.BuildersKt` (= 2.0.20 で追加
        //              された top-level factory file) を参照するため NoClassDefFoundError。
        //              よって 2.0.10 は引き続き compat-k200 が dispatch する)
        //   - 2.0.20 → compat-k202 (minVersion 2.0.20、 BuildersKt を含む)
        //   - 2.0.21 → compat-k202
        //   - 2.1.0+ → compat-k210+ (minVersion 2.1.0 が 2.0.20 より新しい)
        //
        // 2.0.10 については task-080 で fullyExpandedType reflection shim 経由で compat-k200
        // 自体が drift-safe なため別 module を必要としない。 2.0.20+ で集中して surface した
        // IrVarargImplKt / IrConstImplKt / IrGetEnumValueImplKt 削除 + BuildersKt 導入は
        // compat-k202 で吸収する。
        override val minVersion: String = "2.0.20"

        override fun create(): CompatContext = CompatContextImpl()
    }

    /**
     * Kotlin 2.0.20 〜 2.0.21 baseline 向けの **診断 factory** SSoT (task-121 で
     * 旧 `checker/K202CaptureCodeDiagnostics.kt` から本 nested object に集約)。
     *
     * 構造は [K200Diagnostics][me.tbsten.capture.code.compat.k200.CompatContextImpl.K200Diagnostics]
     * と同形。 2.0.x patch 内では `KtDiagnosticFactory*` constructor / `error0`/`error1` delegate
     * の signature は drift 無し。 詳細な背景は K200 側 KDoc 参照。
     */
    public object K202Diagnostics {

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
            RootDiagnosticRendererFactory.registerFactory(K202CaptureCodeDefaultMessages)
        }

        private object K202CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
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
