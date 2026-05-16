package me.tbsten.capture.code.compat.k230

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k230.checker.K230CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230RendererMapShim
import me.tbsten.capture.code.error.CaptureCodeDiagnosticMessages
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement

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

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        runK230IrTransform(moduleFragment, pluginContext, config)
    }

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

    override fun containingFilePathOf(context: CheckerContext): String? =
        // Kotlin 2.3.x: `CheckerContext.containingFile` accessor が削除され、
        // `containingFilePath: String?` に置き換わった (drift D12)。
        context.containingFilePath

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        type.fullyExpandedType(session)

    override fun loadFileText(file: IrFile): String? = SourceTextExtractor.loadFileText(file)

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
        config: CaptureCodePluginConfig,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    ) {
        with(extensionStorage) {
            FirExtensionRegistrarAdapter.registerExtension(firRegistrar)
            IrGenerationExtension.registerExtension(irExtension)
        }
    }

    override fun diagnosticFactory(id: String): Any? = K230Diagnostics.MAP[id]

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
                        CaptureCodeDiagnosticMessages.render(
                            CaptureCodeDiagnosticMessages.MARKER_PARAMETER_TYPE_INVALID,
                        ),
                        KtDiagnosticRenderers.TO_STRING,
                    )
                    put(
                        CC_MARKER_FILLER_REQUIRES_DEFAULT,
                        CaptureCodeDiagnosticMessages.render(
                            CaptureCodeDiagnosticMessages.MARKER_FILLER_REQUIRES_DEFAULT,
                        ),
                        KtDiagnosticRenderers.TO_STRING,
                    )
                    put(
                        CC_MARKER_IS_EXPECT,
                        CaptureCodeDiagnosticMessages.render(
                            CaptureCodeDiagnosticMessages.MARKER_IS_EXPECT,
                        ),
                    )
                    put(
                        CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                        CaptureCodeDiagnosticMessages.render(
                            CaptureCodeDiagnosticMessages.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                        ),
                        org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
                    )
                }
            }
        }
    }
}
