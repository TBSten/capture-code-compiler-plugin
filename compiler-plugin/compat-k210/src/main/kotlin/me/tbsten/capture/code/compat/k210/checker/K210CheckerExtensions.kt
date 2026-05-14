package me.tbsten.capture.code.compat.k210.checker

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.1.x baseline 向けの `FirAdditionalCheckersExtension` 群。 K200 版と同型。
 */
internal class K210MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K210CaptureCodeMarkerClassChecker)
    }
}

internal class K210MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K210MarkerAnnotationChecker)
    }
}

internal class K210CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K210CapturedSourcesCallChecker)
    }
}

internal class K210ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(K210ExpressionSiteCollector)
    }
}
