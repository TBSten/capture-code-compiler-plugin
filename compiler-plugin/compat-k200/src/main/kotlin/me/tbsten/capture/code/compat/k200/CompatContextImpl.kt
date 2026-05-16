package me.tbsten.capture.code.compat.k200

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k200.checker.FullyExpandedTypeShim
import me.tbsten.capture.code.compat.k200.checker.K200CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerCheckersExtension
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

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        runK200IrTransform(moduleFragment, pluginContext, config)
    }

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

    override fun containingFilePathOf(context: CheckerContext): String? =
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        // task-080: 2.0.x の `fullyExpandedType(session)` overload は 2.0.20 で削除されたため、
        // reflection shim 経由で 2.0.0 baseline / 2.0.20+ 両方を吸収する。
        FullyExpandedTypeShim.expand(type, session)

    override fun loadFileText(file: IrFile): String? = SourceTextExtractor.loadFileText(file)

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
        config: CaptureCodePluginConfig,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    ) {
        with(extensionStorage) {
            FirExtensionRegistrarAdapter.registerExtension(firRegistrar)
            IrGenerationExtension.registerExtension(irExtension)
        }
    }

    override fun diagnosticFactory(id: String): Any? = K200Diagnostics.MAP[id]

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
     * 文面は [CaptureCodeDiagnosticMessages] (compat 共有 SSoT) を引き続き参照する
     * (task-122 で英語 only 化予定)。
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
            )
        }

        init {
            RootDiagnosticRendererFactory.registerFactory(K200CaptureCodeDefaultMessages)
        }

        /**
         * Renderer factory: ID → メッセージ。 文面は [CaptureCodeDiagnosticMessages] (compat 共有 SSoT)
         * を参照する。
         */
        private object K200CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
            override val MAP: KtDiagnosticFactoryToRendererMap =
                KtDiagnosticFactoryToRendererMap("CaptureCode").apply {
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
