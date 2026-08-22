package me.tbsten.capture.code.feature.markerDefinition

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * [CaptureCodeMarkerRegistry] に登録された **flatten 済 marker FqN 文字列** から
 * IR class symbol を解決する共通ヘルパ。
 *
 * registry の marker FqN は FIR phase の `DiscoverMarkerClass` が
 * `classId.asSingleFqName().asString()` で文字列化したもので、 nested class の場合
 * package / class の境界情報が失われている (例: package `example` の
 * `object Ns { annotation class Snippet }` は `example.Ns.Snippet` に flatten される)。
 * 単純な `ClassId.topLevel(FqName(markerFqn))` は package=`example.Ns` / class=`Snippet`
 * と解釈してしまい、 nested marker を resolve できない (= `null`)。
 *
 * 本ヘルパは flatten FqN の分割候補を **package segment 数が多い順** (= まず top-level
 * class と仮定し、 見つからなければ nested 段数を 1 つずつ増やす) に試し、 最初に
 * [IrPluginContext.referenceClass] が非 null を返した symbol を採用する:
 *
 * 1. `ClassId(example.Ns, Snippet)` — top-level 解釈 (従来動作。 top-level marker はここで確定)
 * 2. `ClassId(example, Ns.Snippet)` — 1 段 nested
 * 3. `ClassId(<root>, example.Ns.Snippet)` — root package 解釈 (最後の保険)
 *
 * package と nested class 名の組が実在するのは高々 1 通り (Kotlin は同一 FqN の
 * package と class の同時宣言をコンパイルエラーにする) なので、 先勝ちで曖昧さは生じない。
 *
 * @param markerFqn [CaptureCodeMarkerRegistry] 由来の flatten 済 marker FqN (non-blank)
 * @return 解決できた [IrClassSymbol]、 どの分割候補でも resolve 不能なら `null`
 */
internal fun IrPluginContext.referenceMarkerClass(markerFqn: String): IrClassSymbol? {
    val segments = markerFqn.split('.')
    // packageSegmentCount = 先頭何個の segment を package と見なすか。
    // segments.size - 1 (= top-level 解釈) から 0 (= root package 解釈) まで順に試す。
    for (packageSegmentCount in segments.size - 1 downTo 0) {
        val packageFqName =
            if (packageSegmentCount == 0) FqName.ROOT
            else FqName(segments.take(packageSegmentCount).joinToString("."))
        val relativeClassName = FqName(segments.drop(packageSegmentCount).joinToString("."))
        val symbol = referenceClass(ClassId(packageFqName, relativeClassName, false))
        if (symbol != null) return symbol
    }
    return null
}
