package me.tbsten.capture.code.compat.k240rc

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import me.tbsten.capture.code.compat.k240rc.checker.K240RcCaptureCodeDiagnostics

/**
 * task-088 / task-089 regression test。 K230 と完全に同じ理由で必要。
 * 詳細は `K230CaptureCodeDiagnosticsStaticInitTest` の KDoc 参照。
 */
class K240RcCaptureCodeDiagnosticsStaticInitTest : StringSpec({

    "K240Rc CaptureCodeDiagnostics の outer/inner object は static init 循環依存無しで load できる" {
        val factory = K240RcCaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION
        factory shouldNotBe null
    }

    "全 diagnostic factory が <clinit> 完了後に read できる" {
        K240RcCaptureCodeDiagnostics.CC_MARKER_VISIBILITY_VIOLATION shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_MARKER_RETENTION_VIOLATION shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_MARKER_TARGET_EMPTY shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_MARKER_PARAMETER_TYPE_INVALID shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_MARKER_FILLER_REQUIRES_DEFAULT shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_MARKER_IS_EXPECT shouldNotBe null
        K240RcCaptureCodeDiagnostics.CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE shouldNotBe null
    }

    "renderer factory の MAP が outer init 完了後に lazy 展開できる" {
        val rendererFactory = K240RcCaptureCodeDiagnostics.getRendererFactory()
        rendererFactory.MAP shouldNotBe null
    }
})
