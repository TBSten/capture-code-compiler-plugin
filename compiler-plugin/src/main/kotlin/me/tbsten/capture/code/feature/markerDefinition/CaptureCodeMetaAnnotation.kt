package me.tbsten.capture.code.feature.markerDefinition

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * `@CaptureCode` メタアノテーション本体の identity (FqName / ClassId)。
 *
 * runtime ライブラリ (`:annotation`) で `me.tbsten.capture.code.CaptureCode` として定義されており、
 * FIR / IR の両 phase で marker 判定の anchor として参照される。
 *
 * SSOT は本 object のみ。`:annotation` package パスが変わった場合はここを更新する。
 */
public object CaptureCodeMetaAnnotation {

    /** `@CaptureCode` の package。 */
    public val packageFqName: FqName = FqName("me.tbsten.capture.code")

    /** `@CaptureCode` の class 名。 */
    public val shortName: Name = Name.identifier("CaptureCode")

    /** `@CaptureCode` の完全修飾名。 */
    public val fqName: FqName = packageFqName.child(shortName)

    /** `@CaptureCode` の `ClassId` (top-level annotation class)。 */
    public val classId: ClassId = ClassId(packageFqName, shortName)
}
