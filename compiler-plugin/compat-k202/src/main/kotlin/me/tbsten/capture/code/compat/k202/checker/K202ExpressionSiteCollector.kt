package me.tbsten.capture.code.compat.k202.checker

import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
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
 * Kotlin 2.0.10 〜 2.0.21 patch level 向けの **Logic B-fir** checker (式 annotation の site collector)。
 *
 * task-081 で compat-k200 から fork した版。 2.0.21 で `FirLiteralExpression` の `<T>` 型パラが
 * 削除されている (FIR drift D1) ため、 非ジェネリック版を直接 dispatch する。
 */
internal object K202ExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

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
                // task-081: 2.0.21 で FirLiteralExpression の `<T>` 型パラは削除されている (FIR drift D1)。
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
