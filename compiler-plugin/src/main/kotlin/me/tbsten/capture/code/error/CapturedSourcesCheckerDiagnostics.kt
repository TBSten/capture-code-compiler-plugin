package me.tbsten.capture.code.error

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.psi.KtElement

/**
 * `capturedSources<T>()` 呼び出し (Logic G: `CapturedSourcesCallChecker`) の compile error の定義。
 *
 * Logic F の `CaptureCodeDiagnostics.kt` との責務分離 / 並列作業時のファイル名衝突回避のため、
 * **`CapturedSourcesCheckerDiagnostics.kt`** という prefix 付き専用ファイルに切り出している。
 *
 * 設計上の取り決め:
 *
 * - メッセージ文面を [CaptureCodeDiagnosticMessages] (bilingual SSOT) に分離
 * - `CC_<feature>_<rule>` 命名規則に合わせて property 名を整理
 *   ([CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE])
 * - `Suggested fix:` ヒントをメッセージに追加
 *
 * Renderer は `RootDiagnosticRendererFactory.registerFactory` で 1 度だけ登録される。
 * 同一 JVM 上で複数コンパイル (kctfork の連続 test 実行など) が走っても二重登録にならないよう、
 * [ensureRegistered] で AtomicBoolean ガードを置く。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic G を参照。
 */
internal object CapturedSourcesCheckerDiagnostics {

    /**
     * `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — `capturedSources<T>()` の `T` が `@CaptureCode`
     * メタ付き annotation 型ではないときに発する error。
     *
     * パラメータ A (String): T の表示用文字列 (FqN ベース)。
     *
     * メッセージは [CapturedSourcesCheckerDiagnosticRendererFactory] の `MAP` で定義する。
     */
    val CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE: KtDiagnosticFactory1<String> =
        KtDiagnosticFactory1(
            name = "CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE",
            severity = Severity.ERROR,
            defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
            psiType = KtElement::class,
        )

    private val registered = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * renderer factory を `RootDiagnosticRendererFactory` に 1 度だけ登録する。
     *
     * checker の `check` メソッドから呼ばれる想定で、複数回呼ばれても二重登録しない。
     */
    fun ensureRegistered() {
        if (registered.compareAndSet(false, true)) {
            RootDiagnosticRendererFactory.registerFactory(CapturedSourcesCheckerDiagnosticRendererFactory)
        }
    }
}

/**
 * [CapturedSourcesCheckerDiagnostics] の各 factory に対するメッセージ renderer。
 *
 * 文面の SSOT は [CaptureCodeDiagnosticMessages]。`CAPTURECODE_LOCALE` 環境変数で
 * 英語 / 日本語 / 併記を切替できる (default = 併記)。
 *
 * `RootDiagnosticRendererFactory` に登録することで、`reporter.reportOn(...)` 呼び出しが
 * 適切なエラーメッセージ付きで compile error として出力されるようになる。
 * 未登録の場合でも compile error 自体は発生するが、メッセージが generic になる。
 */
internal object CapturedSourcesCheckerDiagnosticRendererFactory : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap =
        KtDiagnosticFactoryToRendererMap("CapturedSourcesChecker").also { map ->
            map.put(
                CapturedSourcesCheckerDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                CaptureCodeDiagnosticMessages.render(
                    CaptureCodeDiagnosticMessages.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
                ),
                org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
            )
        }
}
