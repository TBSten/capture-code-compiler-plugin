package me.tbsten.capture.code.feature.expression_annotation

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * 式 annotation (`@Marker (expr)`) 検出のための `FirAdditionalCheckersExtension`。
 *
 * design §5 Logic B-fir の式起源側を担う。FIR phase で全 expression を訪問する
 * [CaptureCodeFirExpressionSiteCollector] を `expressionCheckers.basicExpressionCheckers` に
 * 登録することで、`@Marker (expr)` が付いた式の offset と marker FqN を
 * `CaptureCodeExpressionSiteRegistry` に push する経路を確立する。
 *
 * `CaptureCodeFirExtensionRegistrar` の `configurePlugin` で `+::ExpressionAnnotationCheckersExtension`
 * として登録する。
 */
internal class ExpressionAnnotationCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(CaptureCodeFirExpressionSiteCollector)
    }
}
