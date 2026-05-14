package me.tbsten.capture.code.testapp.kmp.sanity

import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.kmp.commonbasic.CommonBasicMarker

// ============================================================================
// sanity: wasmJs target で `capturedSources<T>()` の IR 書き換えが
// 起動することを確認するための最小コード。 詳細は jsTest 側 Sanity.kt のコメント
// 参照。
// ============================================================================
internal object IrFileEntrySanity {
    val captured: List<CommonBasicMarker> = capturedSources<CommonBasicMarker>()
}
