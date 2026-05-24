package me.tbsten.capture.code.testapp.basic

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSource
import me.tbsten.capture.code.capturedSources

// ============================================================================
// `capturedSource<T>()` (単数版 API) の end-to-end 検証。 各 marker は本ファイル
// 内で **ちょうど 1 件** のサイトに付け、 単数版が IR phase で `IrConstructorCall`
// 1 つに inline 置換されることを実機 compile + reflection で確認する。
//
// 0 件 / 複数件 compile error は kctfork unit test (`CapturedSourceSingleTest`) 側で
// 検証済 — published plugin 経由の integration テストとしては happy path に限定。
// ============================================================================

// ----------------------------------------------------------------------------
// property を 1 件だけキャプチャ
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleProp_Marker(val source: Source = Source())

@SingleProp_Marker
val singlePropOnly = "hi"

// ----------------------------------------------------------------------------
// class を 1 件だけキャプチャ
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleClass_Marker(val source: Source = Source())

@SingleClass_Marker
internal class SingleClassOnly

// ----------------------------------------------------------------------------
// 全 filler (Source / SourceLocation / CaptureKind) を持つ marker
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleFull_Marker(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

@SingleFull_Marker
internal fun singleFullCaptureFn() = 42

// ----------------------------------------------------------------------------
// user-defined parameter を持つ marker (= 単数版でも user arg が保たれる)
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleUserArg_Marker(
    val id: Int,
    val label: String = "untitled",
    val source: Source = Source(),
)

@SingleUserArg_Marker(id = 42, label = "primary")
internal fun singleUserArgFn() = Unit

// ----------------------------------------------------------------------------
// 単数版と複数版を同じ marker で並行使用 (干渉なし)
// ----------------------------------------------------------------------------
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleCoexist_Marker(val source: Source = Source())

@SingleCoexist_Marker
val singleCoexistOnly = 1

class CapturedSourceSingleCasesTest : StringSpec({

    "single property capture: capturedSource<T>() returns the one instance" {
        capturedSource<SingleProp_Marker>() shouldBe SingleProp_Marker(
            source = Source(value = "val singlePropOnly = \"hi\""),
        )
    }

    "single class capture: capturedSource<T>() returns the one instance" {
        capturedSource<SingleClass_Marker>() shouldBe SingleClass_Marker(
            source = Source(value = "internal class SingleClassOnly"),
        )
    }

    "single function with all fillers: source/location/kind populated" {
        val captured = capturedSource<SingleFull_Marker>()
        captured.source shouldBe Source(value = "internal fun singleFullCaptureFn() = 42")
        captured.kind shouldBe CaptureKind(value = CaptureKind.Kind.FUNCTION)
        captured.location.packageName shouldBe "me.tbsten.capture.code.testapp.basic"
    }

    "single capture preserves user-defined parameter values" {
        val captured = capturedSource<SingleUserArg_Marker>()
        captured.id shouldBe 42
        captured.label shouldBe "primary"
        captured.source shouldBe Source(value = "internal fun singleUserArgFn() = Unit")
    }

    "capturedSource<T>() and capturedSources<T>() agree on the single site" {
        val single = capturedSource<SingleCoexist_Marker>()
        val many = capturedSources<SingleCoexist_Marker>()
        many.size shouldBe 1
        single shouldBe many[0]
        single shouldBe SingleCoexist_Marker(source = Source(value = "val singleCoexistOnly = 1"))
    }
})
