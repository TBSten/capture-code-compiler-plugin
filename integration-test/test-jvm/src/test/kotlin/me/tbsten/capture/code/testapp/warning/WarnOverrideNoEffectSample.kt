package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-123: `CC_MARKER_OVERRIDE_NO_EFFECT` 発火サンプル。
//
// 本 module (`:integration-test:test-jvm`) では Gradle DSL config は素のデフォルト
// (`includeKdoc = true`, `dedent = true`, `includeLineInfo = true`, ...) で動く。
// この marker は `includeKdoc = Override.Yes` を立てているが、 global default も
// `true` なため override は意味を持たず、 compile 時に `CC_MARKER_OVERRIDE_NO_EFFECT`
// warning が発火する。
//
// runtime 動作はそのままで、 capture 内容は通常 marker と同じ。 warning が compile
// log に乗ることは目視 / CI で確認 (= runtime assertion はここでは行わない)。
// ============================================================================

@CaptureCode(includeKdoc = CaptureCode.Override.Yes)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnOverrideNoEffectMarker(val source: Source = Source())

/**
 * Target with a redundant `includeKdoc = Yes` override on its marker.
 */
@WarnOverrideNoEffectMarker
internal fun warnOverrideNoEffect_target(): String = "warn"

class WarnOverrideNoEffectSample : StringSpec({

    "runtime: redundant override still captures the target source" {
        val captured = capturedSources<WarnOverrideNoEffectMarker>()
        captured.size shouldBe 1
    }
})
