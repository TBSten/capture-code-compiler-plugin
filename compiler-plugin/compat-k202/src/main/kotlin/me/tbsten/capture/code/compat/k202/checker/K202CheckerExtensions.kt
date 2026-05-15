package me.tbsten.capture.code.compat.k202.checker

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirBasicExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Kotlin 2.0.x baseline 向けの `FirAdditionalCheckersExtension` 群。
 *
 * 各 extension は対応する logic (A / F / G / B-fir) の checker を 1 つだけ登録する。
 * 責務分離を維持しつつ、 K2.0 baseline で compile された **唯一の checker bytecode** を
 * 含む module として配置することで runtime abstract method drift を吸収する。
 */
internal class K202MarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K202CaptureCodeMarkerClassChecker)
    }
}

internal class K202MarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(K202MarkerAnnotationChecker)
    }
}

internal class K202CapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K202CapturedSourcesCallChecker)
    }
}

internal class K202ExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirBasicExpressionChecker> =
            setOf(K202ExpressionSiteCollector)
    }
}
