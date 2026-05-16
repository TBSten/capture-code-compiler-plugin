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
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull

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
 */
public class ValidateCapturedSourcesCall {

    /**
     * Diagnostic factories used by this logic.
     */
    public interface Diagnostics {
        public val capturedSourcesTNotCaptureCode: KtDiagnosticFactory1<String>
    }

    public operator fun invoke(
        context: CheckerContext,
        reporter: DiagnosticReporter,
        expression: FirFunctionCall,
        compat: CompatContext,
        diagnostics: Diagnostics,
    ) {
        if (!expression.isCapturedSourcesCall()) return

        val typeArgument = expression.firstTypeArgumentOrNull() ?: return
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
