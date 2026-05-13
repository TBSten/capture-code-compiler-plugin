package me.tbsten.capture.code.fir.checker

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * Logic F (task-010) で導入された marker annotation 診断 checker を登録する
 * `FirAdditionalCheckersExtension`。
 *
 * Logic A の `CaptureCodeMarkerCheckersExtension` (registration only) とは責務を分離している:
 *
 * - Logic A の checker: marker annotation を **発見して registry に登録** する (副作用のみ、診断なし)
 * - 本 extension の checker: marker annotation の **制約違反を診断** する (`reporter.reportOn(...)`)
 *
 * checker は `MarkerAnnotationChecker` で実装されており、本 extension は登録 wiring のみを担う。
 * 将来 Logic F の checker が増えた場合は [regularClassCheckers] に追加する。
 *
 * `CaptureCodeFirExtensionRegistrar` が `+::CaptureCodeFirAdditionalCheckersExtension` で
 * 登録する。
 */
internal class CaptureCodeFirAdditionalCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(MarkerAnnotationChecker)
    }
}
