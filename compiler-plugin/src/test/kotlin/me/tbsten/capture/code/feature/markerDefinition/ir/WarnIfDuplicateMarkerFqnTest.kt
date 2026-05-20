package me.tbsten.capture.code.feature.markerDefinition.ir

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn.WarnIfDuplicateMarkerFqn
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * task-127: `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` warning の unit test。
 *
 * registry を直接操作して duplicate registration をシミュレートし、
 * [WarnIfDuplicateMarkerFqn] が期待通り [MessageCollector] に warning を出力するかを確認する。
 *
 * end-to-end の integration test (= 実際の FIR phase で 2 つの declaration が同 FQN を
 * register するケース) は kotlin compiler の通常運用では起こりにくい (= 同 file に同名
 * annotation class を 2 個書くと compile error)。 ここでは registry の API を直接叩いて
 * 「同 FQN を 2 度 register」 状態を構築する。
 */
class WarnIfDuplicateMarkerFqnTest : StringSpec({

    beforeEach {
        CaptureCodeMarkerRegistry.reset()
    }

    afterEach {
        CaptureCodeMarkerRegistry.reset()
    }

    "no duplicates: warning is not emitted" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo", sourceFilePath = "Foo.kt")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Bar", sourceFilePath = "Bar.kt")

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.shouldBeEmpty()
    }

    "single duplicate FQN: one warning is emitted with offending FQN in the message" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo", sourceFilePath = "common/Foo.kt")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo", sourceFilePath = "jvm/Foo.kt")

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.size shouldBe 1
        val report = collector.reports.single()
        report.severity shouldBe CompilerMessageSeverity.WARNING
        report.text shouldContain "com.example.Foo"
        // FYI: 文面は MarkerDefinitionWarnings.DUPLICATE_MARKER_FQN.message を MessageFormat で埋めた状態
        report.text shouldContain "Multiple `@CaptureCode` markers"
        // 最初の registration の sourceFilePath が location として渡る
        report.location?.path shouldBe "common/Foo.kt"
    }

    "triple duplicate FQN: warning is emitted only once for the same FQN" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.Snippet")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Snippet")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Snippet")

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.size shouldBe 1
        collector.reports.single().text shouldContain "com.example.Snippet"
    }

    "multiple distinct duplicates: each FQN gets its own warning (deterministic order)" {
        // Foo first, Bar second; duplicates added in the same relative order
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Bar")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Bar")

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.size shouldBe 2
        // 追加順 (= 最初の duplicate 観測順) で deterministic に並ぶ
        collector.reports.map { it.text }.also { texts ->
            texts[0] shouldContain "com.example.Foo"
            texts[1] shouldContain "com.example.Bar"
        }
    }

    "registerMarkerOptions also contributes to duplicate detection" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.WithOptions")
        CaptureCodeMarkerRegistry.registerMarkerOptions(
            "com.example.WithOptions",
            CaptureCodeMarkerOptions(includeKdoc = CaptureCodeMarkerOptions.Override.Yes),
        )

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.size shouldBe 1
        collector.reports.single().text shouldContain "com.example.WithOptions"
    }

    "duplicate FQN with no sourceFilePath: warning is emitted without a location" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.NoPath")
        CaptureCodeMarkerRegistry.registerMarker("com.example.NoPath")

        val collector = RecordingMessageCollector()
        WarnIfDuplicateMarkerFqn()(collector)

        collector.reports.size shouldBe 1
        collector.reports.single().location shouldBe null
    }

    "MessageCollector.NONE is accepted (silent)" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.Silent")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Silent")

        // NONE collector を渡しても crash しない (= 既存 unit test と非破壊な互換)
        WarnIfDuplicateMarkerFqn()(MessageCollector.NONE)
    }

    "registry: duplicateMarkerFqns returns the FQNs in registration order" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.A")
        CaptureCodeMarkerRegistry.registerMarker("com.example.B")
        CaptureCodeMarkerRegistry.registerMarker("com.example.A") // dup-1 of A
        CaptureCodeMarkerRegistry.registerMarker("com.example.C")
        CaptureCodeMarkerRegistry.registerMarker("com.example.B") // dup-1 of B

        CaptureCodeMarkerRegistry.duplicateMarkerFqns() shouldContainExactly listOf("com.example.A", "com.example.B")
    }

    "registry: registrationsFor returns entries scoped to the given FQN" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo", sourceFilePath = "a.kt")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Bar", sourceFilePath = "b.kt")
        CaptureCodeMarkerRegistry.registerMarker("com.example.Foo", sourceFilePath = "c.kt")

        val fooRegs = CaptureCodeMarkerRegistry.registrationsFor("com.example.Foo")
        fooRegs.size shouldBe 2
        fooRegs.map { it.sourceFilePath } shouldContainExactly listOf("a.kt", "c.kt")
        CaptureCodeMarkerRegistry.registrationsFor("com.example.Bar").size shouldBe 1
        CaptureCodeMarkerRegistry.registrationsFor("com.example.Other").shouldBeEmpty()
    }

    "registry: reset clears registrations along with markers" {
        CaptureCodeMarkerRegistry.registerMarker("com.example.A")
        CaptureCodeMarkerRegistry.registerMarker("com.example.A")
        CaptureCodeMarkerRegistry.duplicateMarkerFqns() shouldContain "com.example.A"

        CaptureCodeMarkerRegistry.reset()

        CaptureCodeMarkerRegistry.duplicateMarkerFqns().shouldBeEmpty()
        CaptureCodeMarkerRegistry.registrations.shouldBeEmpty()
        CaptureCodeMarkerRegistry.markerFqns.shouldBeEmpty()
    }
})

/**
 * Test-only [MessageCollector] that records every `report(...)` invocation
 * so test cases can assert against them.
 */
private class RecordingMessageCollector : MessageCollector {
    data class Entry(
        val severity: CompilerMessageSeverity,
        val text: String,
        val location: CompilerMessageSourceLocation?,
    )

    val reports: MutableList<Entry> = mutableListOf()

    override fun clear() {
        reports.clear()
    }

    override fun hasErrors(): Boolean = reports.any { it.severity == CompilerMessageSeverity.ERROR }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        reports += Entry(severity, message, location)
    }
}
