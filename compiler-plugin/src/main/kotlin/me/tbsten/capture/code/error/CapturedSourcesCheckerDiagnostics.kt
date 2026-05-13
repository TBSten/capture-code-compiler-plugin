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
 * task-010 が並列で `CaptureCodeDiagnostics.kt` を作成中のため、ファイル名衝突を避けて
 * **`CapturedSourcesCheckerDiagnostics.kt`** という prefix 付き専用ファイルにしている
 * (ticket-011 「並列作業ルール」)。
 *
 * Renderer は `RootDiagnosticRendererFactory.registerFactory` で 1 度だけ登録される。
 * 同一 JVM 上で複数コンパイル (kctfork の連続 test 実行など) が走っても二重登録にならないよう、
 * [ensureRegistered] で AtomicBoolean ガードを置く。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic G を参照。
 */
internal object CapturedSourcesCheckerDiagnostics {

    /**
     * `capturedSources<T>()` の `T` が `@CaptureCode` メタ付き annotation 型ではないときに発する error。
     *
     * パラメータ A (String): T の表示用文字列 (FqN ベース)。
     *
     * メッセージは [CapturedSourcesCheckerDiagnosticRendererFactory] の `MAP` で定義する。
     */
    val CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER: KtDiagnosticFactory1<String> =
        KtDiagnosticFactory1(
            name = "CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER",
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
 * `RootDiagnosticRendererFactory` に登録することで、`reporter.reportOn(...)` 呼び出しが
 * 適切なエラーメッセージ付きで compile error として出力されるようになる。
 * 未登録の場合でも compile error 自体は発生するが、メッセージが generic になる。
 */
internal object CapturedSourcesCheckerDiagnosticRendererFactory : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap =
        KtDiagnosticFactoryToRendererMap("CapturedSourcesChecker").also { map ->
            map.put(
                CapturedSourcesCheckerDiagnostics.CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER,
                "Type parameter T of capturedSources<T>() must be annotated with @CaptureCode. " +
                    "{0} does not have @CaptureCode.",
                org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING,
            )
        }
}
