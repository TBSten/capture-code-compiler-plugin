package me.tbsten.capture.code.compat.k230

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import me.tbsten.capture.code.compat.k230.checker.K230CaptureCodeDiagnostics

/**
 * task-088 / task-089 regression test.
 *
 * ## 何を防ぐか
 *
 * `K230CaptureCodeDiagnostics` (outer object) と `K230CaptureCodeDefaultMessages`
 * (inner object) の間に **static initialization 循環依存** があると、 outer の
 * `<clinit>` 中に inner の `<clinit>` が triggered され、 inner 側で outer の
 * **まだ delegate が null の property** を参照して NPE する。
 *
 * 具体的には:
 *
 * ```
 * Caused by: java.lang.NullPointerException: Cannot invoke "ReadOnlyProperty.getValue(...)"
 *   because "K230CaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION$delegate" is null
 *   at K230CaptureCodeDiagnostics$K230CaptureCodeDefaultMessages.<clinit>(K230CaptureCodeDiagnostics.kt:72)
 * ```
 *
 * 修正前は K230 marker class checker が **visibility violation を report する瞬間**
 * (= consumer の marker が `public` で `internal` / `private` でない場合)
 * に NPE で fail し、 consumer に「visibility error を報告する正にその瞬間 NPE」
 * という最悪の UX を見せていた。
 *
 * ## なぜ既存の integration test では検出できなかったか
 *
 * integration test の test fixtures は marker を `internal` で定義しているため
 * visibility check が pass し、 `reportOn(CC_MARKER_VISIBILITY_VIOLATION, ...)`
 * 行が一度も実行されない → `CC_MARKER_VISIBILITY_VIOLATION` field の class load
 * が trigger されない → outer/inner の <clinit> 順序問題が露呈しない、 という
 * 二重の隠蔽が起きていた。
 *
 * ## なぜ本 test で防げるか
 *
 * `K230CaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION` を **直接 read** して
 * outer object の `<clinit>` を強制的に走らせる。 もし循環依存が再発したら、
 * このシンプルな read だけで NPE が出るため即 fail する (kctfork や Kotlin
 * compile session 不要)。
 */
class K230CaptureCodeDiagnosticsStaticInitTest : StringSpec({

    "K230CaptureCodeDiagnostics の outer/inner object は static init 循環依存無しで load できる" {
        // 単に property を read するだけで <clinit> が走る。 NPE が出なければ pass。
        val factory = K230CaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION
        factory shouldNotBe null
    }

    "全 diagnostic factory が <clinit> 完了後に read できる" {
        K230CaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION shouldNotBe null
        K230CaptureCodeDiagnostics.CC_MARKER_RETENTION_VIOLATION shouldNotBe null
        K230CaptureCodeDiagnostics.CC_MARKER_TARGET_EMPTY shouldNotBe null
        K230CaptureCodeDiagnostics.CC_MARKER_PARAMETER_TYPE_INVALID shouldNotBe null
        K230CaptureCodeDiagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT shouldNotBe null
        K230CaptureCodeDiagnostics.CC_MARKER_IS_EXPECT shouldNotBe null
        K230CaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE shouldNotBe null
    }

    "renderer factory の MAP が outer init 完了後に lazy 展開できる" {
        // getRendererFactory() は inner object を返す。 その MAP property に access
        // すると `by lazy` 展開で全 put(CC_*, ...) が走る。 修正前の eager init では
        // 直前の outer init で null delegate 参照になっていた箇所。
        val rendererFactory = K230CaptureCodeDiagnostics.getRendererFactory()
        rendererFactory.MAP shouldNotBe null
    }
})
