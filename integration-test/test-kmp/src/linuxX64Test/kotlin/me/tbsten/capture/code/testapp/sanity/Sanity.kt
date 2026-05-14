package me.tbsten.capture.code.testapp.sanity

import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case101.Snippets_KmpCase101

// ============================================================================
// task-025 sanity: linuxX64 target で `capturedSources<T>()` の IR 書き換えが
// 起動することを確認するための最小コード。 詳細は jsTest 側 Sanity.kt のコメント
// 参照。
// ============================================================================
internal object IrFileEntrySanity {
    val captured: List<Snippets_KmpCase101> = capturedSources<Snippets_KmpCase101>()
}
