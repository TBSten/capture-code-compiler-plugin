package me.tbsten.capture.code.testapp.permarker

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// ============================================================================
// Per-marker option override (task-045): marker 側で `@CaptureCode(...)` の
// parameter を指定すると、global Gradle DSL config を marker 単位で上書きできる。
//
// 本 module (`:integration-test:test-jvm`) は `kotlinCompilerPluginClasspath` 経由で
// compiler plugin を attach しており、 Gradle DSL config は素のデフォルト値が使われる
// (= includeKdoc=true, dedent=true, includeLineInfo=true 等)。 marker 側の override
// で逆方向に倒した動作を end-to-end で検証する。
//
//   - Override.Default → global config に従う (= 既存と完全互換)
//   - Override.Yes / No → marker level の値が global config を上書きする
//
// case A: marker level なし → KDoc が含まれる (global default includeKdoc=true)
// case B: includeKdoc = No → marker level で KDoc を除外
// case C: dedent = No → marker level で indent を保持
// case D: includeLineInfo = No → marker level で startLine/endLine を 0 に倒す
// ============================================================================

// ----------------------------------------------------------------------------
// case A: parameter なしの marker。 global default に従う (KDoc を含む)
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PerMarkerCaseA(val source: Source = Source())

/**
 * Case A: documented function.
 */
@PerMarkerCaseA
internal fun perMarker_caseA_target(): String = "a"

// ----------------------------------------------------------------------------
// case B: includeKdoc を marker 単位で **強制 off**
// ----------------------------------------------------------------------------
@CaptureCode(includeKdoc = CaptureCode.Override.No)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PerMarkerCaseB(val source: Source = Source())

/**
 * Case B: should NOT appear because marker forces includeKdoc=No.
 */
@PerMarkerCaseB
internal fun perMarker_caseB_target(): String = "b"

// ----------------------------------------------------------------------------
// case C: dedent を marker 単位で **強制 off** (indent を保持)
// ----------------------------------------------------------------------------
@CaptureCode(dedent = CaptureCode.Override.No)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PerMarkerCaseC(val source: Source = Source())

internal class PerMarkerCaseC_Outer {
    @PerMarkerCaseC
    fun indented(): String {
        return "c"
    }
}

// ----------------------------------------------------------------------------
// case D: includeLineInfo を marker 単位で **強制 off** (line を 0 に倒す)
// ----------------------------------------------------------------------------
@CaptureCode(includeLineInfo = CaptureCode.Override.No)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PerMarkerCaseD(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@PerMarkerCaseD
internal val perMarker_caseD_flag = true

class PerMarkerOverrideTest : StringSpec({

    "case A: parameter-less @CaptureCode keeps global default (KDoc included)" {
        val captured = capturedSources<PerMarkerCaseA>()
        captured.size shouldBe 1
        captured[0].source.value shouldContain "Case A: documented function"
        captured[0].source.value shouldContain "fun perMarker_caseA_target"
    }

    "case B: marker level includeKdoc=No removes KDoc despite global default" {
        val captured = capturedSources<PerMarkerCaseB>()
        captured.size shouldBe 1
        captured[0].source.value shouldNotContain "Case B: should NOT appear"
        captured[0].source.value shouldContain "fun perMarker_caseB_target"
    }

    "case C: marker level dedent=No preserves indentation" {
        val captured = capturedSources<PerMarkerCaseC>()
        captured.size shouldBe 1
        // class 内部の 4-space インデントが保持される
        captured[0].source.value shouldContain "    fun indented(): String {"
    }

    "case D: marker level includeLineInfo=No zeroes startLine and endLine" {
        val captured = capturedSources<PerMarkerCaseD>()
        captured.size shouldBe 1
        captured[0].location.startLine shouldBe 0
        captured[0].location.endLine shouldBe 0
    }
})
