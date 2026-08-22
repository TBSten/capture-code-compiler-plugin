package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull

/**
 * この [IrType] の class が `@CaptureCode` meta annotation を持つ (= marker として宣言された
 * annotation class である) かを判定する。
 *
 * `@CaptureCode` は BINARY retention なので、 marker class が classpath (別 module の jar /
 * stale IC round で compile 対象から漏れた class file) から deserialize された場合でも
 * `IrClass.annotations` に現れる。 これにより 「T は marker のはずなのに
 * [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry] に居ない」
 * (= marker declaration が今回の compilation unit に含まれていない) 状態を IR phase で
 * 検出できる (bug-001)。
 *
 * annotation の FqN 照合は main module 既存の
 * [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.markerAnnotations]
 * と同じ `annotation.type.classFqName` パターン。 `classOrNull` / `classFqName` は
 * K2.0 baseline から K2.4 まで安定な `org.jetbrains.kotlin.ir.types` の core extension のみを
 * 使い、 compat SPI を増やさない。
 *
 * 複数版 [RewriteCapturedSourcesCall] と単数版
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall.RewriteCapturedSourceCall]
 * の両 logic から参照される (単数版は既に本 package の `BuildMarkerInstance` に依存しているため、
 * helper も本 package に置く)。
 */
internal fun IrType.hasCaptureCodeMetaAnnotation(): Boolean {
    val irClass = classOrNull?.owner ?: return false
    return irClass.annotations.any { annotation ->
        annotation.type.classFqName == CaptureCodeMetaAnnotation.fqName
    }
}
