package me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
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
 * Logic B-fir: expression-site `@Marker(...)` annotation collector.
 *
 * Walks the annotations on a [FirStatement] and, for every annotation whose
 * annotation class is a registered marker, pushes a
 * [CaptureCodeExpressionSiteRegistry.Site] entry so that the IR phase can later
 * rewrite the expression with captured source information.
 *
 * task-119: 各 `compat-kXXX/checker/K{XXX}ExpressionSiteCollector.kt` に分散して
 * いたロジック本体を main module に統一した版。 K2.0 baseline で書き、
 *
 * - `FirLiteralExpression<T>` (K2.0) → `FirLiteralExpression` (K2.0.21+) drift (D1)
 *   は [CompatContext.literalValueOrNull] 経由で吸収。
 * - `CheckerContext.containingFile` (K2.0–K2.2) → `containingFilePath` (K2.3+)
 *   drift (D12) は [CompatContext.containingFilePathOf] 経由で吸収。
 * - `FirCallableSymbol.callableId` の nullability 変更 (K2.3+) は safe call で
 *   吸収。
 */
public class CollectExpressionSite {

    public operator fun invoke(
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
        expression: FirStatement,
        compat: CompatContext,
    ) {
        val annotations = expression.annotations
        if (annotations.isEmpty()) return

        val contextFilePath = compat.containingFilePathOf(context)
        for (annotation in annotations) {
            val markerFqn = annotation.markerFqnOrNull() ?: continue

            val source = expression.source ?: continue
            val filePath = source.containingFilePath() ?: contextFilePath ?: continue
            val startOffset = source.startOffset
            val endOffset = source.endOffset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) continue

            val userArgs = annotation.collectUserArgs(compat)
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

    private fun FirAnnotation.collectUserArgs(compat: CompatContext): Map<String, Any?> {
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
                // drift D1: `FirLiteralExpression<T>` (K2.0) vs `FirLiteralExpression` (K2.0.21+)。
                // CompatContext 経由で literal value を取り出す。
                compat.isLiteralExpression(expr) -> compat.literalValueOrNull(expr)
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
        // task-075: 2.3.0 で `callableId` は nullable 化された (`CallableId?`)。 2.0–2.2.x で
        // 同一の `!!` 相当の挙動を維持しつつ、 2.3.x で compile 通すために safe call にする。
        return resolved.callableId?.asSingleFqName()?.asString()
    }
}
