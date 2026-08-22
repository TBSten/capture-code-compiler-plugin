package me.tbsten.capture.code.testapp.block

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.runWithCaptureCode

// ============================================================================
// runWithCaptureCode(Marker::class) { ... } — block 起源のキャプチャ
// ----------------------------------------------------------------------------
// expression annotation (`@Marker() (expr)`) との違い:
//   - marker に `@Target(AnnotationTarget.EXPRESSION)` が要らない
//   - K2 parser の `@Marker()` 空カッコ制約 (design §13.1) を受けない
//   - `run { ... }` のような wrapper 行が capture 結果に混ざらない
// ============================================================================
@CaptureCode
@Retention(AnnotationRetention.SOURCE)
internal annotation class BasicBlockMarker(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

internal fun basicBlockHost() {
    runWithCaptureCode(BasicBlockMarker::class) {
        println("hoge")
        println("fuga")
    }
}

// ============================================================================
// 戻り値: 型引数を明示せずに R が推論される
// ============================================================================
@CaptureCode
@Retention(AnnotationRetention.SOURCE)
internal annotation class ReturningBlockMarker(val source: Source = Source())

internal fun returningBlockHost(): Int = runWithCaptureCode(ReturningBlockMarker::class) {
    val a = 20
    val b = 22
    a + b
}

// ============================================================================
// 入れ子: 外側の block は内側の呼び出しテキストごと capture する
// ============================================================================
@CaptureCode
@Retention(AnnotationRetention.SOURCE)
internal annotation class OuterBlockMarker(val source: Source = Source())

@CaptureCode
@Retention(AnnotationRetention.SOURCE)
internal annotation class InnerBlockMarker(val source: Source = Source())

internal fun nestedBlockHost() {
    runWithCaptureCode(OuterBlockMarker::class) {
        runWithCaptureCode(InnerBlockMarker::class) {
            println("inner")
        }
    }
}

class BlockCasesTest : StringSpec({

    "block の body だけがキャプチャされ、 wrapper 行は含まれない" {
        capturedSources<BasicBlockMarker>() shouldBe listOf(
            BasicBlockMarker(
                source = Source(value = "println(\"hoge\")\nprintln(\"fuga\")"),
                kind = CaptureKind(value = CaptureKind.Kind.EXPRESSION),
            ),
        )
    }

    "戻り値は block の値になり、 R は推論される" {
        returningBlockHost() shouldBe 42
        capturedSources<ReturningBlockMarker>() shouldBe listOf(
            ReturningBlockMarker(source = Source(value = "val a = 20\nval b = 22\na + b")),
        )
    }

    "入れ子の block はそれぞれ独立した site になる" {
        capturedSources<InnerBlockMarker>() shouldBe listOf(
            InnerBlockMarker(source = Source(value = "println(\"inner\")")),
        )
        capturedSources<OuterBlockMarker>() shouldBe listOf(
            OuterBlockMarker(
                source = Source(
                    value = "runWithCaptureCode(InnerBlockMarker::class) {\n" +
                        "    println(\"inner\")\n" +
                        "}",
                ),
            ),
        )
    }
})
