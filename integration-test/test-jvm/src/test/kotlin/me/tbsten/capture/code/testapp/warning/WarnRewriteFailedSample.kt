package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-135: `CC_CAPTUREDSOURCES_REWRITE_FAILED` warning の発火ドキュメント。
//
// 本 warning は **registered marker FqN が IR phase で resolve 不能** な場合に発火する
// (= FIR phase の marker registry には乗ったが、 `IrPluginContext.referenceClass(...)`
// が `null` を返すケース)。 これは典型的に KMP cross-module setup で、 marker class
// が `commonMain` 側にあって `jvmMain` の IR phase からは class symbol が見えない、
// あるいは別 module の class が registry snapshot 経由でだけ伝わっている、 という
// 状況で起こる。
//
// 同一 single-module compilation では発火条件を再現できない (= marker が registry に
// 乗るためには `@CaptureCode` annotated class が同一 module 内に declared されており、
// その class symbol は IR phase でも resolve できるため)。 そのため本 sample は
// **「正常系: marker resolve が成功して空でない list が返る」 ことを runtime で
// assert する sanity check** + **発火条件の documentation** という構成にする。
//
// 真の発火 verify は KMP integration-test module (将来追加予定) または
// `:compiler-plugin` の kctfork unit test で外部からの artifact resolve を mock する
// 方が現実的。 task-135 では documentation + factory 登録 / SSoT 整備のみを対象とする。
//
// 実機で `--info` を付けて compileTestKotlin を走らせても、 本 marker は同一 module の
// declaration が紐づくため warning は出ない (= silent success が期待挙動)。
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnRewriteFailedMarker(val source: Source = Source())

/**
 * Same-module declaration to demonstrate the **non-firing** baseline:
 * the IR phase can resolve `WarnRewriteFailedMarker` so no warning is emitted.
 */
@WarnRewriteFailedMarker
internal fun warnRewriteFailed_target(): String = "ok"

class WarnRewriteFailedSample : StringSpec({

    "runtime: same-module marker resolves and yields one captured site" {
        // Baseline: when the marker class is co-located with the capture site,
        // IR phase resolves the class symbol and the rewrite succeeds. The
        // CC_CAPTUREDSOURCES_REWRITE_FAILED warning is **not** emitted.
        val captured = capturedSources<WarnRewriteFailedMarker>()
        captured shouldHaveSize 1
    }
})
