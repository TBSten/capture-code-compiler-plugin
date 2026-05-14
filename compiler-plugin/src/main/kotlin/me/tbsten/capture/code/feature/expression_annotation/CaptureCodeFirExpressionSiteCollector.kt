package me.tbsten.capture.code.feature.expression_annotation

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.error.CaptureCodeFillerClassIds
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
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
 * Logic B-fir (design §5) の **式 annotation 側** を担う FIR basic expression checker。
 *
 * IR phase では式 annotation が残らない (spike で観測済) ため、本 checker が FIR phase で
 * 全 expression を訪問し、`expression.annotations` 内の marker を発見次第
 * [CaptureCodeExpressionSiteRegistry] に push する。IR phase の collector
 * (`K200CapturedSourcesCollector.collectExpressionSites`) がそれを読み出して
 * `CapturedSite(kind = EXPRESSION)` に変換する。
 *
 * ## 何を push するか
 *
 * - **filePath**: FIR の `expression.source` (`KtPsiSourceElement.psi.containingFile.virtualFilePath`)
 *   から取る。LightTree 環境では PSI が無いため null になりうる。その場合は site を諦める
 *   (R3: LightTree 環境では path 経由のフォールバックが必要だが、現状は PSI 経由のみサポート)。
 * - **startOffset / endOffset**: **annotation を持つ expression 自身** の source range (= 式そのもの)。
 *   `FirAnnotation.source` は `@Marker` 部分のみを指すため使えない (spike で観測済)。
 * - **markerFqn**: `annotation.annotationTypeRef.coneType.classId` の `asSingleFqName()`。
 * - **userArgs**: FIR `annotation.argumentMapping` を name → primitive/enum FqN の Map に変換。
 *   現状は filler のみが入った marker (ケース #7 / #67 など) を確実に handle するため、最小実装で OK。
 *
 * ## なぜ FIR phase で expression annotation を観察できるのか
 *
 * `FirBasicExpressionChecker.check` は **すべての** `FirStatement` (FirAnnotatedExpression / FirCall /
 * FirReturnExpression / etc.) で呼ばれる。`FirStatement.annotations` には その statement に直接
 * 付与された annotation が残っているので、`@Marker (expr)` 形のように expr に直接付いた marker は
 * 拾える。spike Case C / D / E / F / H で実機確認済み。
 *
 * ## 同じ expression が複数回 visit されるケース
 *
 * spike では Case E (1 行に 2 つの marker) で 2 つの FirAnnotation が独立に観測された。重複 push は
 * registry 側で気にしない (=「FIR の検査順 ≒ source 順」を素直に push し、IR phase で必要なら
 * distinct 化)。本 checker は **markerFqn / startOffset / endOffset / filePath が同じ site** は
 * skip して登録を 1 件に抑える。
 */
internal object CaptureCodeFirExpressionSiteCollector : FirBasicExpressionChecker(MppCheckerKind.Common) {

    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        val annotations = expression.annotations
        if (annotations.isEmpty()) return

        // [containingFile.sourceFile.path] は LightTree モード (PSI 無) 環境での fallback。
        // K2 + kctfork のテスト fixture では FIR が `KtLightSourceElement` を使うため、 PSI 経由で
        // file path を取れず、 `context.containingFile?.sourceFile?.path` (= `KtSourceFile.path`)
        // を採用する。 production の Gradle compile では PSI 経路が動く想定だが、安全のために
        // 両方の経路を提供する。
        val contextFilePath = context.containingFile?.sourceFile?.path
        for (annotation in annotations) {
            // marker 判定は **session service** ではなく **process-scoped registry**
            // ([CaptureCodeMarkerRegistry]) を見る。FIR の declaration checker と expression checker
            // は **同一 FIR session の checker phase 内で順不同に呼ばれる** ため、
            // declaration checker が先に走って registry に登録するとは限らない。
            //
            // この問題を回避するために、 本 checker は marker 判定をせず **すべての annotation** を
            // 暫定的に push し、 IR phase 側で改めて [CaptureCodeMarkerRegistry.isMarker] で
            // フィルタする 2 段階方式を採る。IR phase は declaration / expression 両 checker が
            // 完了してから走る (= phase ordering 上後段) ので、 IR 側では registry が完成している。
            val markerFqn = annotation.markerFqnOrNull() ?: continue

            val source = expression.source ?: continue
            // file path は PSI 経由 → containingFile (LightTree mode 対応) の順で取る
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
            // 同 expression が複数回訪問される / 同じ site を 2 回 push しないため、簡易重複排除。
            if (CaptureCodeExpressionSiteRegistry.allSites.any { it == site }) continue
            CaptureCodeExpressionSiteRegistry.addSite(site)
        }
    }

    /**
     * `FirAnnotation` の type の FqN 文字列を返す。
     *
     * marker 判定は **本 checker 内では行わない** (上述の phase ordering 問題のため)。
     * 本 method は単に annotation type の FqN を取り出すだけで、 IR phase 側で
     * `CaptureCodeMarkerRegistry.isMarker(fqn)` による filter を行う。
     *
     * `IrFile` の cached file text に対する substring 抽出を IR phase で行うため、 marker でない
     * annotation を push しても collector は file path mismatch (= IR file との照合に失敗) or
     * `K200CapturedSourcesCollector.collectExpressionSites()` 内の filter で除外される。
     * 暫定 push のオーバーヘッドは小さい。
     */
    private fun FirAnnotation.markerFqnOrNull(): String? {
        val classId = annotationTypeRef.coneType.classId ?: return null
        return classId.asSingleFqName().asString()
    }

    /**
     * `KtSourceElement` から containing file の virtual file path (絶対パスを期待) を取り出す。
     *
     * PSI が利用可能な場合 (= `KtPsiSourceElement`) は `psi.containingFile.virtualFilePath` を返す。
     * LightTree 環境 (= PSI が無い) では現状 `null` を返し、本 site は諦める。
     * spike 観測で、 production の Gradle compile では PSI 経由が基本利用できることを確認済。
     */
    private fun KtSourceElement.containingFilePath(): String? {
        if (this is KtPsiSourceElement) {
            val path = psi.containingFile?.virtualFile?.path
            if (path != null) return path
            // fallback: virtualFilePath が null のテスト fixture では psi.containingFile?.name のみ
            return psi.containingFile?.name
        }
        return null
    }

    /**
     * `FirAnnotation.argumentMapping` から名前 → primitive / enum FqN の Map を構築する。
     *
     * 現状サポート:
     * - `FirConstExpression` (primitive: Int / Long / String / Boolean / Double / Float / Char)
     * - `FirGetClassCall` の type (KClass パラメータ)
     * - enum entry の `FirPropertyAccessExpression` / `FirQualifiedAccessExpression` (resolved symbol 経由)
     *
     * 非対応 (将来拡張):
     * - array (`vararg`)
     * - nested annotation
     *
     * filler 型 (`Source` / `SourceLocation` / `CaptureKind`) は本 map に **含めない**。
     * IR phase で `K200CapturedSourcesRewriter` の filler builder が値を自動生成するため。
     */
    private fun FirAnnotation.collectUserArgs(): Map<String, Any?> {
        val mapping = argumentMapping.mapping
        if (mapping.isEmpty()) return emptyMap()
        val fillerFqns = setOf(
            CaptureCodeFillerClassIds.Source.asFqNameString(),
            CaptureCodeFillerClassIds.SourceLocation.asFqNameString(),
            CaptureCodeFillerClassIds.CaptureKind.asFqNameString(),
        )
        val compat = CaptureCodeCompatHolder.context
        val result = linkedMapOf<String, Any?>()
        for ((name, expr) in mapping) {
            val typeFqn = expr.resolvedType.classId?.asSingleFqName()?.asString()
            if (typeFqn != null && typeFqn in fillerFqns) continue
            // `FirLiteralExpression<*>` は Kotlin バージョン間で type-parameter の有無が
            // 変わる API drift (D1) があるため、 CompatContext.isLiteralExpression /
            // literalValueOrNull 経由で吸収する。 FirGetClassCall / enum entry の解決は
            // 2.0.0 / 2.1.0 で API が安定しているため main module 側で直接ハンドリング。
            val value = when {
                compat.isLiteralExpression(expr) -> compat.literalValueOrNull(expr)
                expr is FirGetClassCall -> {
                    // KClass argument: 型 FqN を文字列で保持 (IR 化の最小サポート)
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

    /**
     * `FirPropertyAccessExpression` / `FirQualifiedAccessExpression` が enum entry を指していれば、
     * その FqN 文字列 (例: `com.example.Verb.GET`) を返す。それ以外は `null`。
     */
    private fun resolveEnumOrNull(expr: FirQualifiedAccessExpression): String? {
        val resolved = expr.calleeReference.toResolvedCallableSymbol() as? FirCallableSymbol<*>
            ?: return null
        return resolved.callableId.asSingleFqName().asString()
    }
}

