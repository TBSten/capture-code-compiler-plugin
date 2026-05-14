package me.tbsten.capture.code.testapp.sanity

import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case101.Snippets_KmpCase101

// ============================================================================
// task-025 sanity: js target で `capturedSources<T>()` の IR 書き換えが起動する
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
// 検証する case: #101 (commonTest marker + commonTest use site)。 全 target で
// 同じく成立すべき最小シナリオを 1 件選んでいる (target 別シナリオ #102/#104/
// #105 は jvmTest で網羅、 task-021)。
// ============================================================================
internal object IrFileEntrySanity {
    val captured: List<Snippets_KmpCase101> = capturedSources<Snippets_KmpCase101>()
}
