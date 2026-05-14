package me.tbsten.capture.code.fir.marker

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass

/**
 * Logic A の検出経路を提供する `FirAdditionalCheckersExtension`。
 *
 * Kotlin の FIR check phase で全 [FirRegularClass] を訪問し、`ANNOTATION_CLASS` であって
 * かつ `@CaptureCode` メタが付いているものを [CaptureCodeFirMarkerService] (session component)
 * に登録する。
 *
 * 「checker」と命名されているが、本 extension では診断 (compile error / warning) は
 * 発しない。診断は Logic F (`MarkerAnnotationChecker`) の責務で、本 checker はあくまで
 * marker class の「発見と registry への登録」専用。
 *
 * `MppCheckerKind.Common` を指定する理由: marker annotation は v1 では single module 内に閉じている
 * ので、leaf platform module 側でしか検出できない `Platform` ではなく、`Common` で
 * 「declaration の所属 session」で 1 回だけ検出すれば十分。
 */
internal class CaptureCodeMarkerCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(CaptureCodeMarkerClassChecker)
    }
}

/**
 * `@CaptureCode` メタ付き annotation class を発見し、session component に登録する checker。
 *
 * 対象: `ClassKind.ANNOTATION_CLASS` の [FirRegularClass] のみ。
 * 通常の class / object / enum は早期 short-circuit する。
 *
 * 副作用のみで `reporter` には何も report しない (診断は Logic F の `MarkerAnnotationChecker` が担う)。
 */
internal object CaptureCodeMarkerClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    override fun check(
        declaration: FirRegularClass,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (declaration.classKind != ClassKind.ANNOTATION_CLASS) return
        context.session.captureCodeMarkerService.registerIfMarker(declaration)
    }
}
