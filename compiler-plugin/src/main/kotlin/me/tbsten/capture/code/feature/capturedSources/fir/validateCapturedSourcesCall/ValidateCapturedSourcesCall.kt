package me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance

/**
 * Logic G: `capturedSources<T>()` type-argument validation.
 *
 * If the user wrote `capturedSources<NotAMarker>()` where `NotAMarker` is not
 * annotated with a `@CaptureCode`-meta annotation, report
 * [Diagnostics.capturedSourcesTNotCaptureCode] so the misuse surfaces as a
 * compile error rather than a silent no-op.
 *
 * task-119: 各 `compat-kXXX/checker/K{XXX}CapturedSourcesCallChecker.kt` に分散
 * していたロジック本体を main module に統一した版。 K2.0 baseline で書き、
 * 2.1.x で package が移動した `toRegularClassSymbol` (drift D2) は
 * [CompatContext.toRegularClassSymbolOrNull] 経由で吸収する。
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の `K{XXX}CapturedSourcesCallChecker`) は以下を保証する責務がある。
 *
 * - `expression: FirFunctionCall` は FIR-resolved な call (= `FirFunctionCallChecker`
 *   を継承した caller checker が `Common` phase の resolution 完了後に invoke
 *   を呼ぶ)。 `expression.calleeReference` は `FirResolvedNamedReference` で
 *   なければならない。 違反時は invoke 冒頭の `require(...)` で fail-fast (=
 *   typical root cause: caller checker が resolution 完了前の phase に登録されている)。
 * - `expression.isCapturedSourcesCall()` (= callee の `callableId` が
 *   `me.tbsten.capture.code.capturedSources` と一致) を pass した場合のみ、
 *   `expression.typeArguments` は 1 個以上であることが Kotlin compiler 側で保証される
 *   (= `public fun <T : Any> capturedSources(): List<CapturedSource>` の signature)。
 *   違反時は `isCapturedSourcesCall()` を pass した直後の `require(...)` で fail-fast
 *   (= typical root cause: runtime API の signature が後方非互換に変更された)。
 * - `compat.toRegularClassSymbolOrNull(coneType, session)` で type 引数の symbol が
 *   解決可能であること。 null fallback は silent OK (= 後段の error 経路 / 別の
 *   compiler error が拾う想定)。 例: `capturedSources<typealias Foo = ...>()`
 *   など FIR phase で resolution 失敗するケース。
 * - `compat: CompatContext` は同 module の `CompatContextImpl` actual 実装で、
 *   `coneTypeOrNullOf` / `toRegularClassSymbolOrNull` / `classIdOf` の SPI が
 *   正しく dispatch される。
 * - `diagnostics.capturedSourcesTNotCaptureCode` は caller の
 *   `K{XXX}CaptureCodeDiagnostics` から取得した `KtDiagnosticFactory1<String>`。
 */
public class ValidateCapturedSourcesCall {

    /**
     * Diagnostic factories used by this logic.
     */
    public interface Diagnostics {
        public val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String>

        /**
         * task-148 (BUG-H provisional warn): emitted when `capturedSources<T>()` is
         * called with `T` resolved to a type parameter (e.g. inside
         * `inline fun <reified T : Annotation> ...`). The IR rewriter cannot bind
         * such calls to a concrete marker class symbol, so the call would silently
         * fall back to the runtime stub and throw `IllegalStateException` at
         * execution time. Surfacing the situation at compile time turns the silent
         * crash into a noisy compile-time signal.
         */
        public val capturedSourcesTIsTypeParameter: KtDiagnosticFactory1<String>
    }

    public operator fun invoke(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
        compat: CompatContext,
        diagnostics: Diagnostics,
    ) {
        require(expression.calleeReference is FirResolvedNamedReference) {
            "ValidateCapturedSourcesCall: expression.calleeReference must be FirResolvedNamedReference " +
                "after FIR resolution, got ${expression.calleeReference::class.simpleName}. " +
                "Typical root cause: caller checker is registered in a phase that runs before name resolution."
        }

        if (!expression.isCapturedSourcesOrSourceCall()) return

        require(expression.typeArguments.isNotEmpty()) {
            "ValidateCapturedSourcesCall: expression.typeArguments must not be empty for " +
                "capturedSources<T>() / capturedSource<T>() calls. Typical root cause: the runtime API signature " +
                "was changed in a non-backwards-compatible way."
        }

        val typeArgument = expression.firstTypeArgumentOrNull(compat) ?: return

        // task-148 (BUG-H provisional warn): T が type parameter のままだと IR rewriter が
        // concrete marker class symbol に bind できず、 runtime stub fallback で
        // `IllegalStateException("CaptureCode compiler plugin is not applied")` が throw
        // される。 silent runtime crash を compile-time warning に格上げして user に通知。
        if (typeArgument is ConeTypeParameterType) {
            val paramName = typeArgument.lookupTag.name.asString()
            reporter.reportOn(
                source = expression.source,
                factory = diagnostics.capturedSourcesTIsTypeParameter,
                a = paramName,
                context = context,
            )
            return
        }

        val classSymbol = compat.toRegularClassSymbolOrNull(typeArgument, context.session) ?: return

        if (classSymbol.hasCaptureCodeMeta(context.session)) return

        val classId = compat.classIdOf(classSymbol) ?: return
        reporter.reportOn(
            source = expression.source,
            factory = diagnostics.capturedSourcesTNotCaptureCode,
            a = classId.asSingleFqName().asString(),
            context = context,
        )
    }

    /**
     * 当該 call が `capturedSources<T>()` (複数版) または `capturedSource<T>()` (単数版) のいずれかを
     * 判定する。 両者は CallableId が異なる別 API だが、 T の型検査 (`@CaptureCode` 必須 / type
     * parameter NG) は完全に共通なので、 1 つの checker でまとめて検査する。 単数版限定の件数検査
     * (= 0 件 / 複数件 → compile error) は FIR phase では `CaptureCodeMarkerRegistry` の状態が
     * 確定していないため IR phase ([me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall.RewriteCapturedSourceCall])
     * 側で行う。
     */
    private fun FirFunctionCall.isCapturedSourcesOrSourceCall(): Boolean {
        val reference = calleeReference as? FirResolvedNamedReference ?: return false
        val symbol = reference.resolvedSymbol as? FirCallableSymbol<*> ?: return false
        return symbol.callableId == CaptureCodeCallableIds.capturedSources ||
            symbol.callableId == CaptureCodeCallableIds.capturedSource
    }

    private fun FirFunctionCall.firstTypeArgumentOrNull(compat: CompatContext): ConeKotlinType? {
        val projection = typeArguments.firstOrNull() as? FirTypeProjectionWithVariance ?: return null
        // drift D13: `FirTypeRef.coneTypeOrNull` の root (`FirResolvedTypeRef.getType()`)
        // は K2.0 baseline と K2.2+ runtime で interface method shape が drift するため
        // SPI 経由で dispatch。
        return compat.coneTypeOrNullOf(projection.typeRef)
    }

    private fun FirRegularClassSymbol.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }
}
