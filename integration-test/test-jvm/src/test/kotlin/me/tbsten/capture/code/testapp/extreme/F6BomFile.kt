package me.tbsten.capture.code.testapp.extreme

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source

// ============================================================================
// F6 — BOM (U+FEFF) を file 先頭に置く
//
// file の最初の bytes が `0xEF 0xBB 0xBF` (= UTF-8 BOM)。 PSI parse + source
// extraction が BOM 由来の offset ズレを起こさないかを観察する。
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Probe_F6_BomFile(val source: Source = Source())

@Probe_F6_BomFile
val bomDeclared = "after BOM"
