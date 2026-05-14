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
 * marker checker (`MarkerAnnotationChecker`) / IR transformer (`:compat-kXXXX` 配下の filler
 * builder 群) の双方が同じ `ClassId` を参照する SSOT として本 object を使う。
 *
 * ## 配置
 *
 * 本 object は `:compiler-plugin:compat` モジュールに `public` で配置されている。
 * `:compat-kXXXX` の filler builder 群と `:compiler-plugin` checker の両方から参照する必要が
 * あるため、 共通の compat module 配下に置いている。 package (`me.tbsten.capture.code.error`)
 * は呼び出し側の import path 変更を不要にする目的でそのまま維持している。
 */
public object CaptureCodeFillerClassIds {

    private val packageFqName: FqName = FqName("me.tbsten.capture.code")

    /** `me.tbsten.capture.code.Source` の [ClassId]。 */
    public val Source: ClassId = ClassId(packageFqName, Name.identifier("Source"))

    /** `me.tbsten.capture.code.SourceLocation` の [ClassId]。 */
    public val SourceLocation: ClassId = ClassId(packageFqName, Name.identifier("SourceLocation"))

    /** `me.tbsten.capture.code.CaptureKind` の [ClassId]。 */
    public val CaptureKind: ClassId = ClassId(packageFqName, Name.identifier("CaptureKind"))

    /** `me.tbsten.capture.code.CaptureKind.Kind` enum の [ClassId]。CaptureKind の nested class。 */
    public val CaptureKindKind: ClassId = CaptureKind.createNestedClassId(Name.identifier("Kind"))

    /** filler 型 3 つの集合。`contains` で素早く判定するための snapshot。 */
    public val all: Set<ClassId> = setOf(Source, SourceLocation, CaptureKind)

    /** 指定した `ClassId` が filler 型かどうかを返す (hot path)。 */
    public fun isFiller(classId: ClassId): Boolean = classId in all
}
