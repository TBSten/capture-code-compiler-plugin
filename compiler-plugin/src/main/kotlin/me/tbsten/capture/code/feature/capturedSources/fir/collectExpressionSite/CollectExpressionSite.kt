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
import org.jetbrains.kotlin.fir.expressions.FirStatement

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
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の `K{XXX}ExpressionSiteCollector` / 拡張 FIR checker) は
 * 以下を保証する責務がある。 違反時は対応する箇所で silent `continue` / early return
 * し、 IR phase の rewrite から該当 expression が drop される (= site が拾われない)
 * 設計のため、 `require(...)` での fail-fast は導入していない。 ただし plugin 開発者
 * の debug を補助するため、 主要な silent skip 経路 (source null / filePath null /
 * invalid offsets) には [CaptureCodeMessageCollectorHolder.reportLogging] で
 * verbose-log を出す。
 *
 * - `expression: FirStatement` は FIR-resolved な statement。 `expression.annotations`
 *   は resolution phase 完了済 (= `coneTypeOrErrorOf(annotationTypeRef)` / `classIdOfType`
 *   が解決可能)。 typical root cause: caller checker が resolution 完了前の phase に
 *   登録されている。
 * - `expression.annotations` が空の場合は冒頭で early-return (= marker annotation
 *   の付いていない statement は無視)。
 * - `annotation.toAnnotationClassId(session)` 経由で得られる `markerFqn` が
 *   [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   に **登録済の marker FQN** であること (= `DiscoverMarkerClass` が先に走り終えている
 *   こと)。 違反時は per-annotation `continue` で silent skip。
 * - `expression.source` が non-null (= synthetic ではない、 ソース由来の expression)。
 *   typical root cause: コンパイラが暗黙生成した node が `FirAnnotation` を持っている
 *   状況。 silent skip は verbose-log で可視化される。
 * - `expression.source.containingFilePath()` か `compat.containingFilePathOf(context)`
 *   のいずれかが non-null。 KMP の klib で resolve できない極端な case 以外は満たされる。
 * - `expression.source` の `startOffset` / `endOffset` が `0 <= startOffset < endOffset`。
 *   UNDEFINED_OFFSET (-1) や逆転 offset は silent skip + verbose-log。
 * - `compat: CompatContext` は同 module の `CompatContextImpl` actual 実装で、
 *   `containingFilePathOf` / `coneTypeOrErrorOf` / `classIdOfType` / `isLiteralExpression` /
 *   `literalValueOrNull` / `resolvedTypeOrNullOf` の SPI が正しく dispatch される。
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
            // task-149: bare file name (= `psi.containingFile.name`) は
            // `CollectDeclarationSite.matchesFile` が **完全一致でしか** 採用しない。
            // 同名 file が別ディレクトリに複数ある実プロジェクトで、 file A の offset が
            // file B の text に適用されて garbage を capture する事故を防ぐため。
            // したがって解決順は 「PSI virtualFile の絶対パス → CheckerContext の file path →
            // (最後の手段) bare file name」 とし、 bare name に落ちたことは verbose-log に残す。
            val resolvedFilePath = source.containingFilePath() ?: contextFilePath
            val filePath = if (resolvedFilePath != null) {
                resolvedFilePath
            } else {
                val bareFileName = source.containingFileNameOrNull()
                if (bareFileName == null) {
                    CaptureCodeMessageCollectorHolder.reportLogging(
                        "[CaptureCode] Expression annotated with marker '$markerFqn' has no " +
                            "resolvable containing file path; skipping expression site.",
                    )
                    continue
                }
                CaptureCodeMessageCollectorHolder.reportLogging(
                    "[CaptureCode] Could not resolve an absolute path for the file containing the " +
                        "expression annotated with marker '$markerFqn'; falling back to the bare " +
                        "file name '$bareFileName'. The expression site is matched only against an " +
                        "exactly equal IR file path, so this capture may be skipped.",
                )
                bareFileName
            }
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

    /**
     * PSI の `virtualFile` から取れる **絶対パス** のみを返す。
     *
     * task-149 以前は取得できない場合に `psi.containingFile.name` (= bare file name) へ
     * fallback していたが、 bare name は同名 file の誤マッチを招くため呼び出し側で
     * 明示的に段階分けする ([containingFileNameOrNull] 参照)。
     */
    private fun KtSourceElement.containingFilePath(): String? =
        (this as? KtPsiSourceElement)?.psi?.containingFile?.virtualFile?.path

    /**
     * PSI の bare file name (= ディレクトリを含まない `Basic.kt` 形式) を返す。
     * 絶対パスも `CheckerContext` 由来の path も取れなかった場合の最終 fallback。
     */
    private fun KtSourceElement.containingFileNameOrNull(): String? =
        (this as? KtPsiSourceElement)?.psi?.containingFile?.name

    private fun FirAnnotation.collectUserArgs(compat: CompatContext): Map<String, UserArgValue> {
        val mapping = argumentMapping.mapping
        if (mapping.isEmpty()) return emptyMap()
        val fillerFqns = setOf(
            CaptureCodeFillerClassIds.Source.asFqNameString(),
            CaptureCodeFillerClassIds.SourceLocation.asFqNameString(),
            CaptureCodeFillerClassIds.CaptureKind.asFqNameString(),
        )
        val convert = ConvertUserArgExpression()
        val result = linkedMapOf<String, UserArgValue>()
        for ((name, expr) in mapping) {
            // drift D13/D14: `FirExpression.resolvedType` + `ConeKotlinType.classId` を
            // SPI 経由で dispatch (root cause は `FirResolvedTypeRef.getType()` の interface
            // method shape drift)。
            val typeFqn = compat.resolvedTypeOrNullOf(expr)
                ?.let { compat.classIdOfType(it) }
                ?.asSingleFqName()?.asString()
            if (typeFqn != null && typeFqn in fillerFqns) continue
            // task-133: 旧 `Any?` 経路を sealed UserArgValue に統合。 bug-004: 変換 branch
            // (literal / ClassRef / enum / unaryMinus 畳み込み / const val 畳み込み / 配列 /
            // nested annotation) は [ConvertUserArgExpression] に切り出した。 変換不能な式は
            // `UserArgValue.UnsupportedExpression` になり、 IR 側で実態に合った warning を
            // 発火して default fallback する (silent 経路なし)。
            result[name.asString()] = convert(expr, compat)
        }
        return result
    }
}
