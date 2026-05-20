package me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite

import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.capturedSources.UserArgValue
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
            val markerFqn = annotation.markerFqnOrNull(compat) ?: continue

            val source = expression.source
            if (source == null) {
                // task-137: synthetic expression は IR/FIR が暗黙生成した非ソース由来の
                // node であり、 そもそも capture 対象になるべきソース範囲を持たない。
                // user 通常 build には影響しないが、 plugin 開発者が「該当 marker が
                // 期待した位置を採用していない」 原因を debug する手掛かりとして LOGGING
                // で skip を可視化する。
                CaptureCodeMessageCollectorHolder.reportLogging(
                    "[CaptureCode] Expression annotated with marker '$markerFqn' has no " +
                        "source element (synthetic expression); skipping expression site.",
                )
                continue
            }
            val filePath = source.containingFilePath() ?: contextFilePath ?: continue
            val startOffset = source.startOffset
            val endOffset = source.endOffset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) {
                // task-137: UNDEFINED_OFFSET (-1) や逆転 offset を持つ source は通常
                // generated code / 解析失敗箇所 で、 ソース文字列を抽出できないため skip。
                // verbose build で観測できるよう LOGGING で記録する。
                CaptureCodeMessageCollectorHolder.reportLogging(
                    "[CaptureCode] Expression annotated with marker '$markerFqn' has " +
                        "invalid source offsets ($startOffset..$endOffset); skipping expression site.",
                )
                continue
            }

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

    private fun FirAnnotation.markerFqnOrNull(compat: CompatContext): String? {
        // drift D13/D14: `FirTypeRef.coneType` + `ConeKotlinType.classId` を SPI 経由で
        // dispatch。 main module を K2.0 baseline で compile した bytecode が drift する
        // `FirResolvedTypeRef.getType()` / `ConeClassLikeLookupTag.getClassId()` interface
        // method shape を、 各 compat-kXXX module で再 link して吸収する。
        val coneType = compat.coneTypeOrErrorOf(annotationTypeRef)
        val classId = compat.classIdOfType(coneType) ?: return null
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

    private fun FirAnnotation.collectUserArgs(compat: CompatContext): Map<String, UserArgValue> {
        val mapping = argumentMapping.mapping
        if (mapping.isEmpty()) return emptyMap()
        val fillerFqns = setOf(
            CaptureCodeFillerClassIds.Source.asFqNameString(),
            CaptureCodeFillerClassIds.SourceLocation.asFqNameString(),
            CaptureCodeFillerClassIds.CaptureKind.asFqNameString(),
        )
        val result = linkedMapOf<String, UserArgValue>()
        for ((name, expr) in mapping) {
            // drift D13/D14: `FirExpression.resolvedType` + `ConeKotlinType.classId` を
            // SPI 経由で dispatch (root cause は `FirResolvedTypeRef.getType()` の interface
            // method shape drift)。
            val typeFqn = compat.resolvedTypeOrNullOf(expr)
                ?.let { compat.classIdOfType(it) }
                ?.asSingleFqName()?.asString()
            if (typeFqn != null && typeFqn in fillerFqns) continue
            // task-133: 旧 `Any?` 経路を sealed UserArgValue に統合。 各分岐で対応する
            // UserArgValue subclass を組み立てる。 解決失敗時は `UserArgValue.NullValue`
            // で null-safe に統合し、 caller (IR phase) で exhaustive when できるようにする。
            val arg: UserArgValue = when {
                // drift D1: `FirLiteralExpression<T>` (K2.0) vs `FirLiteralExpression` (K2.0.21+)。
                // CompatContext 経由で literal value を取り出す。
                compat.isLiteralExpression(expr) -> {
                    // SPI は Any? を返す (circular dep 回避のため。 CompatContext.kt の
                    // literalValueOrNull KDoc 参照)。 main 側で sealed UserArgValue に wrap。
                    val raw = compat.literalValueOrNull(expr)
                    if (raw == null) UserArgValue.NullValue
                    else UserArgValue.wrapLiteralValue(raw) ?: UserArgValue.NullValue
                }
                expr is FirGetClassCall -> {
                    val firstArg = expr.arguments.firstOrNull()
                    val classFqn = firstArg
                        ?.let { compat.resolvedTypeOrNullOf(it) }
                        ?.let { compat.classIdOfType(it) }
                        ?.asSingleFqName()?.asString()
                    if (classFqn != null) UserArgValue.ClassRef(classFqn) else UserArgValue.NullValue
                }
                expr is FirPropertyAccessExpression ->
                    resolveEnumOrNull(expr)?.let(UserArgValue::EnumRef) ?: UserArgValue.NullValue
                expr is FirQualifiedAccessExpression ->
                    resolveEnumOrNull(expr)?.let(UserArgValue::EnumRef) ?: UserArgValue.NullValue
                else -> UserArgValue.NullValue
            }
            result[name.asString()] = arg
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
