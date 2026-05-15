// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k230.checker

import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
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
 * Kotlin 2.2.x baseline 向けの **Logic B-fir** ロジック本体 (式 annotation の site collector)。
 *
 * Kotlin 2.2.x で `FirBasicExpressionChecker.check` が context-parameter signature に切り替わったため、
 * checker 本体は Java shim ([K230ExpressionSiteCollectorShim]) に置き、 本オブジェクトに
 * ロジック本体を切り出してデリゲートする。 ロジック自体は K210 版と同一。
 */
public object K230ExpressionSiteCollectorLogic {
    @JvmStatic
    public fun run(
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
        expression: FirStatement,
    ) {
        val annotations = expression.annotations
        if (annotations.isEmpty()) return

        // Kotlin 2.2.x で `CheckerContext.containingFile: FirFile?` accessor が削除され、
        // `containingFilePath: String?` に置き換わった。 2.0/2.1 系では `containingFile?.sourceFile?.path`
        // を使うが、 ここ (k230) では `containingFilePath` を直接参照する。
        val contextFilePath = context.containingFilePath
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
        // task-075: 2.3.0 で `callableId` は nullable 化された (`CallableId?`)。 2.0–2.2.x で
        // 同一の `!!` 相当の挙動を維持しつつ、 2.3.x で compile 通すために safe call にする。
        return resolved.callableId?.asSingleFqName()?.asString()
    }
}
