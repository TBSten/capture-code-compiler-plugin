package me.tbsten.capture.code.compat.k220.checker

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
 * Kotlin 2.1.x baseline 向けの **診断 factory** SSOT。
 *
 * task-072 で `:compiler-plugin` main module の `CaptureCodeDiagnostics` /
 * `CapturedSourcesCheckerDiagnostics` を **compat-k220 layer に複製** したもの。
 * 中身は [me.tbsten.capture.code.compat.k200.checker.K200CaptureCodeDiagnostics] と同一だが、
 * 各 compat module は **自分自身の baseline kotlin-compiler-embeddable** に対して compile される
 * ため、 diagnostic factory class の bytecode が baseline 専用に固定される。
 *
 * Kotlin 2.0.x / 2.1.x の `KtDiagnosticFactory0` constructor / `error0()` delegate は同一 signature
 * のため本ファイルの内容は K200 と機械的コピーで OK。 2.2.x で signature drift が入ったら
 * **compat-k220** を新設してそこで再宣言する想定。
 */
public object K220CaptureCodeDiagnostics {

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
        )

    init {
        RootDiagnosticRendererFactory.registerFactory(K220CaptureCodeDefaultMessages)
    }

    private object K220CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
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
