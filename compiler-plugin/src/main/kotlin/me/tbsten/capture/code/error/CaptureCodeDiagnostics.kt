package me.tbsten.capture.code.error

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderers
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory

/**
 * Capture Code compiler plugin の **診断 SSOT**。
 *
 * 各 FIR checker / IR transformer が `reporter.reportOn(...)` で発する診断 ID とメッセージを
 * 1 箇所に集約する。 設計上の取り決め:
 *
 * - メッセージ文面を [CaptureCodeDiagnosticMessages] (bilingual SSOT) に分離
 * - `CC_<feature>_<rule>` 命名規則に合わせて property 名を整理 (例: [CC_MARKER_VISIBILITY_VIOLATION])
 * - `Suggested fix:` ヒントを各メッセージに追加
 * - 環境変数 `CAPTURECODE_LOCALE` で英語 / 日本語 / 併記を切替 (default = 併記)
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic F、§8.5 (`error/` SSOT) を参照。
 *
 * ## Logic F: marker annotation の制約違反 (6 種類)
 *
 * | DiagnosticFactory                              | 違反内容                                                       |
 * |------------------------------------------------|----------------------------------------------------------------|
 * | [CC_MARKER_VISIBILITY_VIOLATION]               | visibility が `internal` / `private` のいずれでもない          |
 * | [CC_MARKER_RETENTION_VIOLATION]                | `@Retention` が `SOURCE` 以外                                  |
 * | [CC_MARKER_TARGET_EMPTY]                       | `@Target(...)` が空 / 未指定                                   |
 * | [CC_MARKER_PARAMETER_TYPE_INVALID]             | Kotlin annotation 制約 外の parameter 型                       |
 * | [CC_MARKER_FILLER_REQUIRES_DEFAULT]            | filler 型 parameter にデフォルト値がない                       |
 * | [CC_MARKER_IS_EXPECT]                          | marker 自身が `expect` 宣言                                    |
 */
internal object CaptureCodeDiagnostics {

    // ----------------------------------------------------------------
    // Logic F: marker annotation の制約違反
    // ----------------------------------------------------------------

    /** `CC_MARKER_VISIBILITY_VIOLATION` — visibility が `internal` / `private` のいずれでもない。 */
    val CC_MARKER_VISIBILITY_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>(
        SourceElementPositioningStrategies.VISIBILITY_MODIFIER,
    )

    /** `CC_MARKER_RETENTION_VIOLATION` — `@Retention` が `SOURCE` 以外 (default `RUNTIME` 含む)。 */
    val CC_MARKER_RETENTION_VIOLATION: KtDiagnosticFactory0 by error0<PsiElement>()

    /** `CC_MARKER_TARGET_EMPTY` — `@Target(...)` が空 / 未指定。 */
    val CC_MARKER_TARGET_EMPTY: KtDiagnosticFactory0 by error0<PsiElement>()

    /**
     * `CC_MARKER_PARAMETER_TYPE_INVALID` — Kotlin annotation 制約 外の parameter 型。
     *
     * パラメータ名を message に埋め込むため `KtDiagnosticFactory1<String>`。
     */
    val CC_MARKER_PARAMETER_TYPE_INVALID: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /**
     * `CC_MARKER_FILLER_REQUIRES_DEFAULT` — filler 型 (`Source` / `SourceLocation` / `CaptureKind`)
     * parameter にデフォルト値がない。
     */
    val CC_MARKER_FILLER_REQUIRES_DEFAULT: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /** `CC_MARKER_IS_EXPECT` — marker 自身が `expect` 宣言 (design §7.6 で非対応)。 */
    val CC_MARKER_IS_EXPECT: KtDiagnosticFactory0 by error0<PsiElement>()

    init {
        RootDiagnosticRendererFactory.registerFactory(CaptureCodeDefaultMessages)
    }

    /**
     * 上記 DiagnosticFactory に対応するメッセージのレンダラ。
     *
     * 文面の SSOT は [CaptureCodeDiagnosticMessages]。`CAPTURECODE_LOCALE` 環境変数で
     * 英語 / 日本語 / 併記を切替できる (default = 併記)。
     *
     * `RootDiagnosticRendererFactory` に init で登録されるため、Kotlin compiler の
     * 通常のメッセージ出力経路で利用される。テストでは `result.messages` 内に文字列が
     * 出力されることを確認している。
     */
    private object CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
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
            }
    }
}
