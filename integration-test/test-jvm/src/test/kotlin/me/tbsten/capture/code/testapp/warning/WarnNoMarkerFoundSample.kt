package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-120-B Phase 7: `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` 発火サンプル。
//
// `:integration-test:test-jvm` の `build.gradle.kts` は `warnOnEmptyCapture = true`
// を CLI option として渡している (= opt-in flag が有効)。 この marker は
// **どの宣言にも付けられていない** ため、 `capturedSources<WarnNoMarkerFoundEmpty>()`
// の戻り値は空 list になり、 同時に compile 時に
// `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warning が compile log に乗る。
//
// 実機 verify は以下のコマンドで確認できる:
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin \
//         | grep CC_CAPTUREDSOURCES_NO_MARKER_FOUND
//
// runtime 側は「空 list が返る」 ことだけ assert する (warning の発火そのものは
// kotest 経由では拾えない / compile log 観測が必要)。
//
// 関連: `.local/tmp/task-123-no-marker-found-spike.md` (false positive 検証 + opt-in
// flag pattern 採用根拠)。
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnNoMarkerFoundEmpty(val source: Source = Source())

class WarnNoMarkerFoundSample : StringSpec({

    "runtime: empty marker yields empty captured list" {
        // No declaration in this compilation carries `@WarnNoMarkerFoundEmpty`,
        // so the rewrite produces `listOf()` and capturedSources returns empty.
        capturedSources<WarnNoMarkerFoundEmpty>().shouldBeEmpty()
    }
})
