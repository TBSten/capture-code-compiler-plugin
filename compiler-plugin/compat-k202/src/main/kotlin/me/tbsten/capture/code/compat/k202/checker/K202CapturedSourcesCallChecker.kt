package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol

/**
 * Kotlin 2.0.x baseline 向けの **Logic G** checker (`capturedSources<T>()` 型引数検査)。
 *
 * task-072 で `:compiler-plugin` main module の `CapturedSourcesCallChecker` を compat-k202 layer
 * に移動した版。 `toRegularClassSymbol(session)` extension は 2.0.x で `fir.types` package、
 * 2.1.x で `fir.resolve` package に移動した (drift D2) ため、 各 compat module 側で適切な
 * import path を使う。
 */
internal object K202CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (!expression.isCapturedSourcesCall()) return

        val typeArgument = expression.firstTypeArgumentOrNull() ?: return
        val classSymbol = typeArgument.toRegularClassSymbol(context.session) ?: return

        if (classSymbol.hasCaptureCodeMeta(context.session)) return

        val classId = classSymbol.classId
        reporter.reportOn(
            source = expression.source,
            factory = K202CaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
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
