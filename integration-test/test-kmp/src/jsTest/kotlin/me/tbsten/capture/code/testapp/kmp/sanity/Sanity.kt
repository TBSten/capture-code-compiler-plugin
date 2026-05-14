package me.tbsten.capture.code.testapp.sanity

import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.commonbasic.CommonBasicMarker

// ============================================================================
// sanity: js target で `capturedSources<T>()` の IR 書き換えが起動する
// ことを確認するための最小コード。
//
// `compileTestKotlinJs` の compile success をもって「IR transformer (FIR phase
// から渡された marker を IR phase で展開し、 `IrFileEntry` 経由でソーステキスト
// を抽出する) が js target でも JVM と同じく動く」ことの証拠とする。
//
// テスト runner は JVM 専用 (Kotest junit5) なので、 本ファイルは実行されない。
// ただし object 内 property として参照されるため dead-code elimination は
// 起こらず、 compile 時に必ず IR transformer が起動する。
//
// 検証するシナリオ: commonTest marker + commonTest use site (KMP 基本)。 全 target で
// 同じく成立すべき最小シナリオを 1 件選んでいる (target 別 / expect-actual /
// intermediate hierarchy シナリオは jvmTest で網羅)。
// ============================================================================
internal object IrFileEntrySanity {
    val captured: List<CommonBasicMarker> = capturedSources<CommonBasicMarker>()
}
