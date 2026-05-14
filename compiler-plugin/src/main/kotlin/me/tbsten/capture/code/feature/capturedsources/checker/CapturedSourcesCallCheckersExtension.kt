package me.tbsten.capture.code.feature.capturedsources.checker

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Logic G の [CapturedSourcesCallChecker] を Kotlin の FIR check phase に登録する extension。
 *
 * Logic A の [me.tbsten.capture.code.fir.marker.CaptureCodeMarkerCheckersExtension] は
 * **declaration checker only** (`@CaptureCode` メタ付き annotation class の発見と登録) であり、
 * 本 extension は **expression checker only** (`capturedSources<T>()` 呼び出しの型引数検査) と
 * 役割が直交している。
 *
 * **なぜ別 extension に分離するか**:
 *
 * - Logic F (marker annotation の visibility / @Retention / @Target / parameter 型診断) は
 *   declaration checker として `CaptureCodeFirAdditionalCheckersExtension` 側に集約される。
 *   expression checker である本 logic は別ファイルに切り出すことで責務の混在と merge conflict を回避
 * - `FirAdditionalCheckersExtension` は 1 plugin module 内に複数登録可能 (Kotlin の plugin
 *   registration は extension instance 単位で集約される)。declaration / expression / type checker
 *   を feature 単位で分けるのは一般的なパターン
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic G、impl-plan §1.1 (feature/<feature>/<logic>/ 配置) 参照。
 */
internal class CapturedSourcesCallCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        // 注: Kotlin 2.0.0 では `FirFunctionCallChecker` 単独の class は提供されておらず、
        // `FirExpressionChecker<FirFunctionCall>` を直接継承するパターンを取る。
        // `ExpressionCheckers.functionCallCheckers` の型もそのまま `Set<FirExpressionChecker<FirFunctionCall>>`。
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> =
            setOf(CapturedSourcesCallChecker)
    }
}
