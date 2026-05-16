// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi opt-in required に
// なり、 また FirRegularClassSymbol#fir 等が SymbolInternals opt-in 必要となった。
@file:OptIn(
    org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class,
    org.jetbrains.kotlin.fir.symbols.SymbolInternals::class,
)

package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull

/**
 * Kotlin 2.2.x baseline 向けの **Logic G** ロジック本体 (`capturedSources<T>()` 型引数検査)。
 *
 * Kotlin 2.2.x で `FirExpressionChecker.check` が context-parameter signature に切り替わったため、
 * checker 本体は Java shim ([K230CapturedSourcesCallCheckerShim]) に置き、 本オブジェクトに
 * ロジック本体を切り出してデリゲートする。 ロジック自体は K210 版と同一。
 */
public object K230CapturedSourcesCallCheckerLogic {
    @JvmStatic
    public fun run(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
    ) {
        if (!expression.isCapturedSourcesCall()) return

        val typeArgument = expression.firstTypeArgumentOrNull() ?: return
        val classSymbol = typeArgument.toRegularClassSymbol(context.session) ?: return

        if (classSymbol.hasCaptureCodeMeta(context.session)) return

        val classId = classSymbol.classId
        reporter.reportOn(
            source = expression.source,
            factory = K230CaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
            a = classId.asSingleFqName().asString(),
            context = context,
        )
    }

    private fun FirFunctionCall.isCapturedSourcesCall(): Boolean {
        val reference = calleeReference as? FirResolvedNamedReference ?: return false
        val symbol = reference.resolvedSymbol as? FirCallableSymbol<*> ?: return false
        return symbol.callableId == CaptureCodeCallableIds.capturedSources
    }

    private fun FirFunctionCall.firstTypeArgumentOrNull(): ConeKotlinType? {
        val projection = typeArguments.firstOrNull() as? FirTypeProjectionWithVariance ?: return null
        return projection.typeRef.coneTypeOrNull
    }

    private fun FirRegularClassSymbol.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }
}
