package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-127: `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` 発火サンプル。
//
// `WarnIfDuplicateMarkerFqn` は **同 compilation 内で同一 marker FQN を 2 回以上
// register** したケースを検出する。 通常 Kotlin compiler は同一 package で同名 class を
// 2 度宣言すると COMPILATION_ERROR で弾くため、 「2 つの marker class が完全に同じ FQN を
// 持つ」 状態を平常 source code 上に作るのは難しい。
//
// よってここでは **single declaration の smoke ケース** のみを置き、 「同 FQN で 1 度しか
// register していない通常運用では warning が出ない / runtime 動作が変わらない」 ことを
// 確認する。 重複 register 自体の挙動 (= warning が deterministic に発火する) は
// `compiler-plugin/src/test/.../markerDefinition/ir/WarnIfDuplicateMarkerFqnTest.kt`
// で registry を直接操作する unit test 経由でカバーする。
//
// 実機 verify が必要なケース (= 同 FQN を意図的にぶつけたい場合) は KMP の `expect`/`actual`
// で同名 marker を declared する pattern や、 buildscript で別 source set を意図的に
// merge する pattern など特殊 setup が必要。 task-127 のスコープ外。
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnDuplicateMarkerFqnMarker(val source: Source = Source())

@WarnDuplicateMarkerFqnMarker
internal fun warnDuplicateMarkerFqn_target(): String = "ok"

class WarnDuplicateMarkerFqnSample : StringSpec({

    "runtime: single (non-duplicate) marker captures its target source" {
        val captured = capturedSources<WarnDuplicateMarkerFqnMarker>()
        captured.size shouldBe 1
    }
})
