package me.tbsten.capture.code.error

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * `:annotation` runtime ライブラリで提供される **filler 型** の `ClassId` SSOT。
 *
 * design §3.1 / §5 Logic F で定義された 3 つの filler 型:
 *
 * - [Source] — `me.tbsten.capture.code.Source`
 * - [SourceLocation] — `me.tbsten.capture.code.SourceLocation`
 * - [CaptureKind] — `me.tbsten.capture.code.CaptureKind`
 *
 * marker annotation がこれらの型のコンストラクタ引数を宣言したとき、
 * Capture Code plugin が compile time に値を埋める。
 *
 * marker checker (`MarkerAnnotationChecker`) / IR transformer (`task-013` で実装予定) の
 * 双方が同じ `ClassId` を参照する SSOT として本 object を使う。
 */
internal object CaptureCodeFillerClassIds {

    private val packageFqName: FqName = FqName("me.tbsten.capture.code")

    /** `me.tbsten.capture.code.Source` の [ClassId]。 */
    val Source: ClassId = ClassId(packageFqName, Name.identifier("Source"))

    /** `me.tbsten.capture.code.SourceLocation` の [ClassId]。 */
    val SourceLocation: ClassId = ClassId(packageFqName, Name.identifier("SourceLocation"))

    /** `me.tbsten.capture.code.CaptureKind` の [ClassId]。 */
    val CaptureKind: ClassId = ClassId(packageFqName, Name.identifier("CaptureKind"))

    /** filler 型 3 つの集合。`contains` で素早く判定するための snapshot。 */
    val all: Set<ClassId> = setOf(Source, SourceLocation, CaptureKind)

    /** 指定した `ClassId` が filler 型かどうかを返す (hot path)。 */
    fun isFiller(classId: ClassId): Boolean = classId in all
}
