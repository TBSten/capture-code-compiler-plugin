package me.tbsten.capture.code.feature.capturedsources.checker

import me.tbsten.capture.code.error.CapturedSourcesCheckerDiagnostics
import me.tbsten.capture.code.feature.capturedsources.CaptureCodeCallableIds
import me.tbsten.capture.code.fir.marker.captureCodeMarkerService
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull

/**
 * Logic G: `capturedSources<T>()` 呼び出しに対する FIR checker。
 *
 * `T` が `@CaptureCode` メタ付き annotation 型 (= Logic A で
 * [me.tbsten.capture.code.fir.marker.CaptureCodeFirMarkerService] に登録された marker class) で
 * **ない** 場合に [CapturedSourcesCheckerDiagnostics.CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER]
 * error を報告する。
 *
 * これがないと、ユーザは `T : Annotation` の generic bound だけを満たす一般 annotation を
 * 渡せてしまい、plugin は何も書き換えないため `capturedSources<T>()` は runtime に
 * `error("CaptureCode compiler plugin is not applied")` を投げる stub 呼び出しのまま残る。
 * 「呼んだのに空リストが返ってきた」ではなく「呼んだら例外」になるが、いずれにせよ
 * compile time に防げる方が断然親切。
 *
 * ## 実装ノート
 *
 * - 対象判定: `FirFunctionCall.calleeReference` → `FirResolvedNamedReference.resolvedSymbol` の
 *   [FirCallableSymbol.callableId] が [CaptureCodeCallableIds.capturedSources] と一致するか
 * - `T` の取得: `typeArguments[0]` を [FirTypeProjectionWithVariance] に cast し
 *   `typeRef.coneTypeOrNull` から `ClassId` を取り出す
 * - marker 判定: `session.captureCodeMarkerService.markerClassIds` に当該 `ClassId` が
 *   含まれているか
 * - 解決できない型引数 (例: スター投影や型推論失敗) はスキップ。これは他の checker が
 *   報告する診断と重複させないため
 *
 * ## 並列 checker 構成
 *
 * task-008 で既に `CaptureCodeMarkerCheckersExtension` (declaration checker only) があるが、
 * 本 checker は expression checker なので別 extension に分離する。これは task-010 (Logic F) が
 * 同じ declaration checker extension を拡張する想定で、ファイル干渉を避けるため。
 * 詳細は [CapturedSourcesCallCheckersExtension] を参照。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic G、§6 Phase ordering 参照。
 */
internal object CapturedSourcesCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        // 1) 呼び出し対象が `me.tbsten.capture.code.capturedSources` か判定
        if (!expression.isCapturedSourcesCall()) return

        // 2) renderer factory を 1 度だけ登録 (runtime register pattern)
        CapturedSourcesCheckerDiagnostics.ensureRegistered()

        // 3) `T` の ConeKotlinType を取得
        val typeArgument = expression.firstTypeArgumentOrNull() ?: return
        val classId = typeArgument.classId ?: return

        // 4) `T` が marker registry にあるかチェック
        val markerClassIds = context.session.captureCodeMarkerService.markerClassIds
        if (classId in markerClassIds) return

        // 5) 違反: error を報告
        reporter.reportOn(
            source = expression.source,
            factory = CapturedSourcesCheckerDiagnostics.CAPTURED_SOURCES_T_NOT_CAPTURE_CODE_MARKER,
            a = classId.asSingleFqName().asString(),
            context = context,
        )
    }

    private fun FirFunctionCall.isCapturedSourcesCall(): Boolean {
        val reference = calleeReference as? FirResolvedNamedReference ?: return false
        val symbol = reference.resolvedSymbol as? FirCallableSymbol<*> ?: return false
        return symbol.callableId == CaptureCodeCallableIds.capturedSources
    }

    private fun FirFunctionCall.firstTypeArgumentOrNull(): ConeKotlinType? {
        val projection = typeArguments.firstOrNull() as? FirTypeProjectionWithVariance ?: return null
        return projection.typeRef.coneTypeOrNull
    }
}
