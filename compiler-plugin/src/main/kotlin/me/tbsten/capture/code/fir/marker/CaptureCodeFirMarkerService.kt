package me.tbsten.capture.code.fir.marker

import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Logic A (`@CaptureCode` メタアノテーションの動的検出) を担う FIR session component。
 *
 * 役割:
 * - declaration checker (`CaptureCodeMarkerClassChecker`) が訪問した annotation class に対し、
 *   `@CaptureCode` メタが付いているかを判定する hot path API ([isMarkerCandidate]) を提供
 * - 検出された marker class の `ClassId` / 完全修飾名を保持し、queries に応答する
 * - 後段の IR phase からも読めるよう、結果を [CaptureCodeMarkerRegistry] (compat module の
 *   compilation-scoped holder) に流し込む
 *
 * 同じ session 内で複数の checker / extension が同じ class を判定する可能性があるため、
 * `seenClassIds` で一度処理した class は短絡する (FIR 標準の `FirCache` を使うほどの hot path ではない)。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic A、§6 Phase ordering を参照。
 */
internal class CaptureCodeFirMarkerService(session: FirSession) : FirExtensionSessionComponent(session) {

    /**
     * 当該 session 内で検出された marker annotation class の `ClassId` 集合。
     *
     * declaration checker 経由で蓄積される。読み取り側は `CaptureCodeMarkerRegistry` の方を見ても
     * よいが、本 service の lifecycle が session スコープであることを保証する用途では
     * こちらの方が source of truth に近い。
     */
    val markerClassIds: MutableSet<ClassId> = mutableSetOf()

    /**
     * 与えられた annotation class が `@CaptureCode` メタを持つ marker class であれば、
     * registry に登録して `true` を返す。
     *
     * 既に登録済みの class に対しては true をそのまま返し、副作用は無い (set semantics)。
     * meta-annotation が付いていない (= 通常の annotation class) 場合は `false` を返す。
     *
     * @param classSymbol annotation class の symbol
     * @return marker として認識された場合 `true`
     */
    fun registerIfMarker(declaration: FirRegularClass): Boolean {
        val classId = declaration.symbol.classId
        if (classId in markerClassIds) return true

        val hasCaptureCode = declaration.annotations.any { annotation ->
            annotation.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId
        }
        if (!hasCaptureCode) return false

        markerClassIds.add(classId)
        CaptureCodeMarkerRegistry.registerMarker(classId.asSingleFqName().asString())
        return true
    }

    /**
     * 与えられた `FqName` の class が marker として登録済みかどうかを返す。
     *
     * `capturedSources<T>()` の checker (Logic G) などから利用される想定。
     */
    fun isMarker(fqName: FqName): Boolean =
        markerClassIds.any { it.asSingleFqName() == fqName }
}

/**
 * `FirSession` から [CaptureCodeFirMarkerService] を取り出す標準アクセサ。
 *
 * `session.captureCodeMarkerService` の形で利用できる。
 */
internal val FirSession.captureCodeMarkerService: CaptureCodeFirMarkerService by FirSession.sessionComponentAccessor()
