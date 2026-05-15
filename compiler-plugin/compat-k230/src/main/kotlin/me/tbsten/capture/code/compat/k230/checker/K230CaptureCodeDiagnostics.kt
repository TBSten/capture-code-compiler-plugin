package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.error.CaptureCodeDiagnosticMessages
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
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
import org.jetbrains.kotlin.psi.KtElement

/**
 * Kotlin 2.3.x baseline 向けの **診断 factory** SSOT。
 *
 * ## 2.2.x → 2.3.x の drift と吸収
 *
 * Kotlin 2.3.0 で diagnostic factory 周りに以下の breaking change が入った:
 *
 * 1. **`RootDiagnosticRendererFactory` の削除**: 旧 `RootDiagnosticRendererFactory.registerFactory(...)`
 *    によるグローバル登録 API は廃止された。 代わりに `KtDiagnosticsContainer` を継承した
 *    object 自体が `getRendererFactory()` で `BaseDiagnosticRendererFactory` を返す形になった。
 * 2. **`error0` / `error1` extension の receiver 変更**: これらは `KtDiagnosticsContainer.()` の
 *    extension に変更され、 単一 object が container を継承していないと呼べない (Kotlin 2.0 で
 *    compile すると `context-receivers` 警告として現れる)。
 * 3. **`KtDiagnosticFactory1` constructor に `rendererFactory: BaseDiagnosticRendererFactory`
 *    parameter が追加**。
 * 4. **`KtDiagnosticFactoryToRendererMap` の constructor が public 化** された (旧 internal)。
 *
 * 本 module では `K230CaptureCodeDiagnostics` を `KtDiagnosticsContainer` 継承の object に
 * 切り替え、 `getRendererFactory()` 経由で `MAP` を返す形にした。 これにより
 * `error0`/`error1` delegate がそのまま呼べる。
 */
public object K230CaptureCodeDiagnostics : KtDiagnosticsContainer() {

    public val CC_MARKER_VISIBILITY_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>(
        SourceElementPositioningStrategies.VISIBILITY_MODIFIER,
    )

    public val CC_MARKER_RETENTION_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>()

    public val CC_MARKER_TARGET_EMPTY: KtDiagnosticFactory0 by error0<PsiElement>()

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

    private object K230CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
        // task-075: 2.3.x で `KtDiagnosticFactoryToRendererMap(String)` constructor は
        // Kotlin metadata 上 `internal` のままなので Java shim 経由で構築する
        // (JVM bytecode 上は public)。
        override val MAP: KtDiagnosticFactoryToRendererMap =
            K230RendererMapShim.create("CaptureCode").apply {
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
