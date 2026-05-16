package me.tbsten.capture.code.compat.k210.checker

import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Kotlin 2.1.x baseline 向けの **Logic B-fir** checker (式 annotation の site collector)。
 *
 * task-072 で `:compiler-plugin` main module から compat-k210 layer に移動した版。 K200 版との
 * 差異は **`FirLiteralExpression` の型パラメータ削除 (drift D1)** で、 K2.0 では
 * `FirLiteralExpression<*>` だったものが K2.1+ では `FirLiteralExpression` になっている。
 */
internal object K210ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        val annotations = expression.annotations
        if (annotations.isEmpty()) return

        val contextFilePath = context.containingFile?.sourceFile?.path
        for (annotation in annotations) {
            val markerFqn = annotation.markerFqnOrNull() ?: continue

            val source = expression.source ?: continue
            val filePath = source.containingFilePath() ?: contextFilePath ?: continue
            val startOffset = source.startOffset
            val endOffset = source.endOffset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) continue

            val userArgs = annotation.collectUserArgs()
            val site = CaptureCodeExpressionSiteRegistry.Site(
                filePath = filePath,
                startOffset = startOffset,
                endOffset = endOffset,
                markerFqn = markerFqn,
                userArgs = userArgs,
            )
            if (CaptureCodeExpressionSiteRegistry.allSites.any { it == site }) continue
            CaptureCodeExpressionSiteRegistry.addSite(site)
        }
    }

    private fun FirAnnotation.markerFqnOrNull(): String? {
        val classId = annotationTypeRef.coneType.classId ?: return null
        return classId.asSingleFqName().asString()
    }

    private fun KtSourceElement.containingFilePath(): String? {
        if (this is KtPsiSourceElement) {
            val path = psi.containingFile?.virtualFile?.path
            if (path != null) return path
            return psi.containingFile?.name
        }
        return null
    }

    private fun FirAnnotation.collectUserArgs(): Map<String, Any?> {
        val mapping = argumentMapping.mapping
        if (mapping.isEmpty()) return emptyMap()
        val fillerFqns = setOf(
            CaptureCodeFillerClassIds.Source.asFqNameString(),
            CaptureCodeFillerClassIds.SourceLocation.asFqNameString(),
            CaptureCodeFillerClassIds.CaptureKind.asFqNameString(),
        )
        val result = linkedMapOf<String, Any?>()
        for ((name, expr) in mapping) {
            val typeFqn = expr.resolvedType.classId?.asSingleFqName()?.asString()
            if (typeFqn != null && typeFqn in fillerFqns) continue
            val value: Any? = when {
                expr is FirLiteralExpression -> expr.value
                expr is FirGetClassCall -> {
                    val classId = expr.arguments.firstOrNull()?.resolvedType?.classId
                    classId?.asSingleFqName()?.asString()
                }
                expr is FirPropertyAccessExpression -> resolveEnumOrNull(expr)
                expr is FirQualifiedAccessExpression -> resolveEnumOrNull(expr)
                else -> null
            }
            result[name.asString()] = value
        }
        return result
    }

    private fun resolveEnumOrNull(expr: FirQualifiedAccessExpression): String? {
        val resolved = expr.calleeReference.toResolvedCallableSymbol() as? FirCallableSymbol<*>
            ?: return null
        return resolved.callableId.asSingleFqName().asString()
    }
}
