// Kotlin 2.2.x: FirChecker 系 base class が DeprecatedForRemovalCompilerApi で opt-in required になったため、 file 単位で OptIn。
@file:OptIn(org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi::class)

package me.tbsten.capture.code.compat.k240rc.checker

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Kotlin 2.2.x baseline 向けの `FirAdditionalCheckersExtension` 群。
 *
 * Kotlin 2.2.x で `FirRegularClassChecker` / `FirBasicExpressionChecker` 等が typealias
 * (= `FirDeclarationChecker<...>` / `FirExpressionChecker<...>`) になったため、 Java shim は
 * 元クラスを継承し、 ここでは underlying generic 型として登録する。
 *
 * checker 本体 (abstract method override) は Java shim 経由で行い、 ロジックは
 * `K240Rc*CheckerLogic` object に切り出している。
 */
internal class K240RcMarkerCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K240RcCaptureCodeMarkerClassCheckerShim.INSTANCE)
    }
}

internal class K240RcMarkerAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirDeclarationChecker<FirRegularClass>> =
            setOf(K240RcMarkerAnnotationCheckerShim.INSTANCE)
    }
}

internal class K240RcCapturedSourcesCallCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(K240RcCapturedSourcesCallCheckerShim.INSTANCE)
    }
}

internal class K240RcExpressionAnnotationCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val basicExpressionCheckers: Set<FirExpressionChecker<FirStatement>> =
            setOf(K240RcExpressionSiteCollectorShim.INSTANCE)
    }
}
