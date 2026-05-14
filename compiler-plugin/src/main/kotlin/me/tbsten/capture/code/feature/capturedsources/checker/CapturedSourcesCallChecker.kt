package me.tbsten.capture.code.feature.capturedsources.checker

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.error.CapturedSourcesCheckerDiagnostics
import me.tbsten.capture.code.feature.capturedsources.CaptureCodeCallableIds
import me.tbsten.capture.code.fir.marker.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneTypeOrNull

/**
 * Logic G: `capturedSources<T>()` 呼び出しに対する FIR checker。
 *
 * `T` が `@CaptureCode` メタ付き annotation 型でない場合に
 * [CapturedSourcesCheckerDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE] error を報告する。
 *
 * これがないと、ユーザは `T : Annotation` の generic bound だけを満たす一般 annotation を
 * 渡せてしまい、plugin は何も書き換えないため `capturedSources<T>()` は runtime に
 * `error("CaptureCode compiler plugin is not applied")` を投げる stub 呼び出しのまま残る。
 *
 * ## 実装ノート: 「registry 経由」ではなく「直接 annotations を見る」設計
 *
 * 初期実装では `session.captureCodeMarkerService.markerClassIds` (= Logic A の declaration checker
 * が蓄積する集合) を参照していた。しかし K2 の FIR check phase は
 * **ファイル単位で interleaved に declaration checker → expression checker が呼ばれる** ため、
 * 別ファイルで定義された marker の `@CaptureCode` 検出が、本 checker の expression check よりも
 * 後になる可能性がある (deepwiki Q&A で確認済み)。
 *
 * そこで、registry の race condition を回避するため、本 checker では:
 *
 * - `T` の `ConeKotlinType` → [FirRegularClassSymbol] を `toRegularClassSymbol(session)` で解決
 * - その class symbol の `annotations` から `@CaptureCode` の `ClassId` が直接見つかるかを確認
 *
 * という戦略を取る。これは declaration の visit 順に依存しない確実な方法。
 *
 * registry の方は IR phase (Logic H = `K200CapturedSourcesTransformer`) で「書き換え対象 marker
 * かどうか」を判定する用途で引き続き利用される (IR phase は FIR 完了後なので race-free)。
 *
 * ## 対象判定
 *
 * `FirFunctionCall.calleeReference` → `FirResolvedNamedReference.resolvedSymbol` の
 * [FirCallableSymbol.callableId] が [CaptureCodeCallableIds.capturedSources] と一致する呼び出しのみ
 * を対象にする。
 *
 * ## 並列 checker 構成
 *
 * Logic A / Logic F の declaration checker extension とは別の expression checker 専用 extension
 * ([CapturedSourcesCallCheckersExtension]) で登録する。責務分離 + 並列開発時の merge conflict 回避目的。
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

        // 4) `T` の class symbol を解決 (annotation を読むため)
        //    `toRegularClassSymbol` extension は Kotlin バージョン間で package 移動 drift (D2)
        //    があるため、 CompatContext.toRegularClassSymbolOrNull 経由で吸収する。
        val compat = CaptureCodeCompatHolder.context
        val classSymbol = compat.toRegularClassSymbolOrNull(typeArgument, context.session)
        if (classSymbol == null) {
            // ClassId は取れるが class symbol を解決できない (= 型推論失敗 / star projection など) ケース。
            // 他の checker が型エラーを報告する前提でここはスキップ。
            return
        }

        // 5) `T` の class が `@CaptureCode` メタアノテーションを持っているかチェック
        if (classSymbol.hasCaptureCodeMeta(context.session)) return

        // 6) 違反: error を報告
        //    classId accessor は drift 対策 (D3) のため CompatContext 経由でアクセスする。
        val classId = compat.classIdOf(classSymbol) ?: return
        reporter.reportOn(
            source = expression.source,
            factory = CapturedSourcesCheckerDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
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

    /**
     * `T` の class が `@CaptureCode` メタアノテーション (
     * [CaptureCodeMetaAnnotation.classId]) を持っているか判定する。
     *
     * `toAnnotationClassId(session)` は `FirAnnotation` 1 つにつき annotation class の
     * 完全修飾識別子 (`ClassId`) を返してくれるので、`@CaptureCode` 1 つで照合できる。
     */
    private fun FirRegularClassSymbol.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }
}
