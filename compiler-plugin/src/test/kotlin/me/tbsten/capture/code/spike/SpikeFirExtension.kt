package me.tbsten.capture.code.spike

import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType

/**
 * task-009 spike: FIR phase での観察用 extension。
 *
 * `FirBasicExpressionChecker` を実装して全 expression を訪問し、 `expression.annotations` を観察する。
 * これにより:
 * - 「FIR phase では式 annotation が `FirStatement.annotations` に乗っているか?」を実機検証 (R1)
 * - source `KtSourceElement` から startOffset / endOffset を取り出して、PSI 上の位置と整合するかを観察 (R2)
 *
 * 観察結果は singleton 的に [SpikeReportHolder.current] 経由で [SpikeReport] に push する。
 * (compiler plugin の extension コンストラクタは reflection ベースで FIR が呼ぶため、
 *  test 側から report を直接 inject できない都合上 holder を経由する)
 */
internal class SpikeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::SpikeFirCheckersExtension
    }
}

internal class SpikeFirCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(SpikeFirBasicChecker)
    }
}

internal object SpikeFirBasicChecker : FirBasicExpressionChecker(MppCheckerKind.Common) {
    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        val report = SpikeReportHolder.current ?: return
        val markerFqns = SpikeReportHolder.markerFqns
        val anns: List<FirAnnotation> = expression.annotations
        if (anns.isEmpty()) return
        for (ann in anns) {
            val classId = ann.annotationTypeRef.coneType.classId ?: continue
            val fqn = classId.asSingleFqName().asString()
            if (fqn !in markerFqns) continue
            val src = ann.source
            val (startOffset, endOffset) = (src?.startOffset ?: -1) to (src?.endOffset ?: -1)
            // `KtSourceElement` には base class に `.text` は存在しない。 PSI 経由でのみ取れる。
            // kctfork 環境では LightTree なので `(non-psi source)` になる想定。
            val psiText = runCatching {
                when (src) {
                    is KtPsiSourceElement -> src.psi.text
                    else -> "(non-psi source)"
                }
            }.getOrDefault("(error)")
            // KtSourceElement には直接 line 番号は無い。 IR phase 側 ([SpikeIrExtension]) で取る方が確実。
            val startLine = -1
            val container = (context.containingDeclarations.lastOrNull()?.let { it::class.simpleName } ?: "?")
            report.firAnnotations += FirAnnotationRecord(
                classFqn = fqn,
                startOffset = startOffset,
                endOffset = endOffset,
                startLine = startLine,
                containingDeclaration = container,
                psiText = psiText,
            )
        }
    }
}

/**
 * FIR の checker は session lifecycle 内で構築されるため、test 側から direct injection できない。
 * test driver は compile 前に [current] に [SpikeReport] をセットし、compile 後に null に戻す。
 *
 * spike test 限定の利用なので thread safety は考慮しない。
 */
internal object SpikeReportHolder {
    @Volatile
    var current: SpikeReport? = null

    @Volatile
    var markerFqns: Set<String> = emptySet()
}
