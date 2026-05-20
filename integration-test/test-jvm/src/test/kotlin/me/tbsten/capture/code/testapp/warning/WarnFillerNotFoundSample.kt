package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-135: `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning の発火ドキュメント。
//
// 本 warning は **Capture Code runtime filler class (Source / SourceLocation /
// CaptureKind) が consumer の classpath に乗っていない** 場合に発火する (= compiler
// plugin は attach されているが `:annotation` runtime dep が missing なケース)。
// IR phase の `BuildMarkerInstance.buildFillerPlan` で `pluginContext.referenceClass(...)`
// が `null` を返したとき、 task-135 までは silent return null で書き換えがスキップされ
// 空 list が runtime に返るのみだった。 task-135 で
// `CC_CAPTUREDSOURCES_FILLER_NOT_FOUND` warning に昇格された。
//
// 本 sample は `:annotation` を normal に依存している (= `build.gradle.kts` の
// `implementation(project(":annotation"))`) ため、 filler class は通常通り resolve
// でき warning は **発火しない** (= silent success が期待挙動)。 真の発火を verify
// するには、 別の test module で **`:annotation` runtime dep を外す + compiler plugin
// のみ attach する** 構成が必要 (= 将来 task-137 等で再現用 sub-module を追加できる)。
//
// 実機で `--info` を付けて compileTestKotlin を走らせても、 本 module は `:annotation`
// に依存しているため warning は出ない。
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnFillerNotFoundMarker(val source: Source = Source())

/**
 * Same-module declaration to demonstrate the **non-firing** baseline:
 * with the `:annotation` runtime on classpath, the filler classes resolve and
 * the rewrite succeeds.
 */
@WarnFillerNotFoundMarker
internal fun warnFillerNotFound_target(): String = "ok"

class WarnFillerNotFoundSample : StringSpec({

    "runtime: filler classes on classpath -> rewrite succeeds, capture is non-empty" {
        // Baseline: with the `:annotation` runtime present, every filler resolves
        // and the rewrite produces a populated list. The
        // CC_CAPTUREDSOURCES_FILLER_NOT_FOUND warning is **not** emitted.
        val captured = capturedSources<WarnFillerNotFoundMarker>()
        captured shouldHaveSize 1
    }
})
