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
 * 各 FIR checker / IR transformer が `reporter.reportOn(...)` で発する診断 ID と
 * メッセージを 1 箇所に集約する。日本語ローカライズ等の i18n は Phase 5 で別途実装する
 * (本 ticket では英語のみ)。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic F、`impl-plan.md` §1.1 (`error/` SSOT) を参照。
 *
 * ## Logic F (task-010): marker annotation の制約違反 (6 種類)
 *
 * | DiagnosticFactory                             | 違反内容                                                       |
 * |------------------------------------------------|----------------------------------------------------------------|
 * | [MARKER_NOT_INTERNAL_OR_PRIVATE]               | visibility が `internal` / `private` のいずれでもない          |
 * | [MARKER_RETENTION_NOT_SOURCE]                  | `@Retention` が `SOURCE` 以外                                  |
 * | [MARKER_TARGET_EMPTY]                          | `@Target(...)` が空 / 未指定                                   |
 * | [MARKER_PARAMETER_TYPE_INVALID]                | Kotlin annotation 制約 外の parameter 型                       |
 * | [MARKER_FILLER_REQUIRES_DEFAULT]               | filler 型 parameter にデフォルト値がない                       |
 * | [MARKER_IS_EXPECT_ANNOTATION]                  | marker 自身が `expect` 宣言                                    |
 */
internal object CaptureCodeDiagnostics {

    // ----------------------------------------------------------------
    // Logic F (task-010): marker annotation の制約違反
    // ----------------------------------------------------------------

    /** marker annotation の visibility が `internal` / `private` のいずれでもない。 */
    val MARKER_NOT_INTERNAL_OR_PRIVATE: KtDiagnosticFactory0 by error0<PsiElement>(
        SourceElementPositioningStrategies.VISIBILITY_MODIFIER,
    )

    /** marker annotation の `@Retention` が `SOURCE` 以外 (= `BINARY` / `RUNTIME` / 未指定の RUNTIME)。 */
    val MARKER_RETENTION_NOT_SOURCE: KtDiagnosticFactory0 by error0<PsiElement>()

    /** marker annotation の `@Target(...)` が空 / 未指定。 */
    val MARKER_TARGET_EMPTY: KtDiagnosticFactory0 by error0<PsiElement>()

    /**
     * marker annotation のパラメータ型が Kotlin annotation 制約外。
     *
     * パラメータ名を message に埋め込むため `KtDiagnosticFactory1<String>`。
     */
    val MARKER_PARAMETER_TYPE_INVALID: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /**
     * marker annotation の filler 型 (`Source` / `SourceLocation` / `CaptureKind`) parameter に
     * デフォルト値がない。
     */
    val MARKER_FILLER_REQUIRES_DEFAULT: KtDiagnosticFactory1<String> by error1<PsiElement, String>()

    /** marker annotation が `expect` 宣言。design §7.6 で非サポート。 */
    val MARKER_IS_EXPECT_ANNOTATION: KtDiagnosticFactory0 by error0<PsiElement>()

    init {
        RootDiagnosticRendererFactory.registerFactory(CaptureCodeDefaultMessages)
    }

    /**
     * 上記 DiagnosticFactory に対応する英語メッセージのレンダラ。
     *
     * `RootDiagnosticRendererFactory` に init で登録されるため、Kotlin compiler の
     * 通常のメッセージ出力経路で利用される。テストでは `result.messages` 内に英語文字列が
     * 出力されることを確認している。
     */
    private object CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
        override val MAP: KtDiagnosticFactoryToRendererMap =
            KtDiagnosticFactoryToRendererMap("CaptureCode").apply {
                put(
                    MARKER_NOT_INTERNAL_OR_PRIVATE,
                    "@CaptureCode marker annotation must be 'internal' or 'private'. " +
                        "Cross-module capture is not supported in v1.",
                )
                put(
                    MARKER_RETENTION_NOT_SOURCE,
                    "@CaptureCode marker annotation must use @Retention(AnnotationRetention.SOURCE).",
                )
                put(
                    MARKER_TARGET_EMPTY,
                    "@CaptureCode marker annotation must specify at least one @Target site " +
                        "(e.g., AnnotationTarget.PROPERTY).",
                )
                put(
                    MARKER_PARAMETER_TYPE_INVALID,
                    "@CaptureCode marker annotation parameter ''{0}'' has an unsupported type. " +
                        "Kotlin annotation parameter types are limited to primitives, String, KClass, " +
                        "enum, annotation, or arrays of these.",
                    KtDiagnosticRenderers.TO_STRING,
                )
                put(
                    MARKER_FILLER_REQUIRES_DEFAULT,
                    "@CaptureCode marker filler parameter ''{0}'' must have a default value " +
                        "(e.g., 'val source: Source = Source()'). The plugin auto-fills filler values " +
                        "at compile time, so use sites do not specify them explicitly.",
                    KtDiagnosticRenderers.TO_STRING,
                )
                put(
                    MARKER_IS_EXPECT_ANNOTATION,
                    "@CaptureCode marker annotation cannot be declared as 'expect'. " +
                        "Markers must be concrete annotation declarations (see design §7.6).",
                )
            }
    }
}
