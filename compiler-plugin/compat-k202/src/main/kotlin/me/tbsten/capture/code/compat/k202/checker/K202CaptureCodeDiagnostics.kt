package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.error.CaptureCodeDiagnosticMessages
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
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
import org.jetbrains.kotlin.psi.KtElement

/**
 * Kotlin 2.0.x baseline 向けの **診断 factory** SSOT。
 *
 * task-072 で `:compiler-plugin` main module の `CaptureCodeDiagnostics` /
 * `CapturedSourcesCheckerDiagnostics` を **compat-k202 layer に複製** したもの。
 *
 * ## なぜ複製が必要か
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
 * メッセージ文面の SSOT は `:compiler-plugin:compat` の [CaptureCodeDiagnosticMessages]
 * (drift-free な data only) を引き続き参照する。
 */
public object K202CaptureCodeDiagnostics {

    // ----------------------------------------------------------------
    // Logic F: marker annotation の制約違反 (6 種類)
    // ----------------------------------------------------------------

    /** `CC_MARKER_VISIBILITY_VIOLATION` — visibility が `internal` / `private` のいずれでもない。 */
    public val CC_MARKER_VISIBILITY_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>(
        SourceElementPositioningStrategies.VISIBILITY_MODIFIER,
    )

    /** `CC_MARKER_RETENTION_VIOLATION` — `@Retention` が `SOURCE` 以外 (default `RUNTIME` 含む)。 */
    public val CC_MARKER_RETENTION_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>()

    /** `CC_MARKER_TARGET_EMPTY` — `@Target(...)` が空 / 未指定。 */
    public val CC_MARKER_TARGET_EMPTY: KtDiagnosticFactory0 by error0<PsiElement>()

    /** `CC_MARKER_PARAMETER_TYPE_INVALID` — Kotlin annotation 制約 外の parameter 型。 */
    public val CC_MARKER_PARAMETER_TYPE_INVALID: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /** `CC_MARKER_FILLER_REQUIRES_DEFAULT` — filler 型 parameter にデフォルト値がない。 */
    public val CC_MARKER_FILLER_REQUIRES_DEFAULT: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /** `CC_MARKER_IS_EXPECT` — marker 自身が `expect` 宣言。 */
    public val CC_MARKER_IS_EXPECT: KtDiagnosticFactory0 by error0<PsiElement>()

    // ----------------------------------------------------------------
    // Logic G: capturedSources<T>() の型引数違反
    // ----------------------------------------------------------------

    /** `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — `T` が `@CaptureCode` 付き marker ではない。 */
    public val CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE: KtDiagnosticFactory1<String> =
        KtDiagnosticFactory1(
            name = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE",
            severity = Severity.ERROR,
            defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
            psiType = KtElement::class,
        )

    init {
        RootDiagnosticRendererFactory.registerFactory(K202CaptureCodeDefaultMessages)
    }

    /**
     * Renderer factory: ID → メッセージ。 文面は [CaptureCodeDiagnosticMessages] (compat 共有 SSOT)
     * を参照する。
     */
    private object K202CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
        override val MAP: KtDiagnosticFactoryToRendererMap =
            KtDiagnosticFactoryToRendererMap("CaptureCode").apply {
                put(
                    CC_MARKER_VISIBILITY_VIOLATION,
                    CaptureCodeDiagnosticMessages.render(
                        CaptureCodeDiagnosticMessages.MARKER_VISIBILITY_VIOLATION,
                    ),
                )
                put(
                    CC_MARKER_RETENTION_VIOLATION,
                    CaptureCodeDiagnosticMessages.render(
                        CaptureCodeDiagnosticMessages.MARKER_RETENTION_VIOLATION,
                    ),
                )
                put(
                    CC_MARKER_TARGET_EMPTY,
                    CaptureCodeDiagnosticMessages.render(
                        CaptureCodeDiagnosticMessages.MARKER_TARGET_EMPTY,
                    ),
                )
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
