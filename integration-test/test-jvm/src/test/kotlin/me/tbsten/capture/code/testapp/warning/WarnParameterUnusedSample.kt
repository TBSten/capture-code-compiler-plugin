package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-128: `CC_MARKER_PARAMETER_UNUSED` 発火サンプル。
//
// `WarnIfParameterUnused` は marker class の constructor parameter のうち
// 「default 値あり / filler 型ではない / 全 site で 1 度も override されない」
// ものに対して 1 度だけ warning を出力する。
//
// このサンプルでは `label` parameter (default = "default") を持つ marker を宣言し、
// その marker を 2 件の declaration に付与するが **どちらも label を override しない** ため、
// compile 時に `CC_MARKER_PARAMETER_UNUSED` が 1 度だけ発火する。
//
// runtime 動作は通常 marker と同じで capturedSources は 2 件のサイトを返す。 warning が
// compile log に乗ることは目視 / CI で確認 (= runtime assertion はここでは行わない)。
//
// 実機 verify は以下で確認できる:
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin \
//         | grep CC_MARKER_PARAMETER_UNUSED
//
// なお、 `Source` 型の `source` parameter は plugin 側で自動 fill される filler 型のため
// unused 判定対象外 (= 仕様通り)。
// ============================================================================

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnParameterUnusedMarker(
    val source: Source = Source(),
    val label: String = "default",
)

@WarnParameterUnusedMarker
internal fun warnParameterUnused_target1(): String = "unused-1"

@WarnParameterUnusedMarker
internal fun warnParameterUnused_target2(): String = "unused-2"

class WarnParameterUnusedSample : StringSpec({

    "runtime: marker with never-overridden default parameter still captures sites" {
        val captured = capturedSources<WarnParameterUnusedMarker>()
        captured.size shouldBe 2
    }
})
