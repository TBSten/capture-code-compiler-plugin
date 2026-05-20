package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-134: `CC_USERARG_ENUM_NOT_FOUND` warning の発火ドキュメント。
//
// 本 warning は **EXPRESSION 起源 (= 式の statement に付いた `@Marker(...)` annotation)
// で enum entry を user arg として指定したが、 IR phase で同名の `IrEnumEntry` が見つから
// なかった** 場合に発火する。 silent failure 経路の昇格 (`Any` 深掘り review report
// §4.3 #3) で、 これまでは null を返して marker 既定値経路に合流していた。
//
// 同一 single-module compilation では発火条件を再現できない:
// 通常 `verb = Verb.NOT_EXIST` のような未解決エントリは FIR phase で `unresolved reference`
// の compile error になり IR phase まで到達しない。 silent failure を引き起こすのは典型的に
// 別 compilation で生成された enum class が後段 IR phase で再 resolve されるような、
// classpath 状態に依存する場面なので、 same-module sanity check ではいずれにせよ green
// になることが期待挙動。
//
// 本 sample は **「正常系: enum entry が正しく解決されて captured list に反映される」 ことを
// runtime で assert する** + **発火条件の documentation** という構成にする。 真の発火 verify
// は将来的に `:compiler-plugin` の kctfork unit test または KMP integration-test で
// `EnumRef(entryFqn = "com.example.Verb.MISSING")` を直接 push する経路を mock する方が
// 現実的。 task-134 では documentation + factory 登録 / SSoT 整備 / `MessageCollector`
// 経由の emit pipeline 整備を対象とする。
//
// 実機で `--info` を付けて compileTestKotlin を走らせても、 本 marker は同一 module の
// enum entry を指している limited capture なので warning は出ない (= silent success が
// 期待挙動)。
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin
// ============================================================================

internal enum class WarnUserArgEnumNotFoundVerb { GET, POST }

@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnUserArgEnumNotFoundMarker(
    val verb: WarnUserArgEnumNotFoundVerb = WarnUserArgEnumNotFoundVerb.GET,
    val source: Source = Source(),
)

/**
 * Declaration-origin baseline: the IR phase deep-copies the original
 * `IrConstructorCall` so the `verb` argument is preserved verbatim and the
 * `CC_USERARG_ENUM_NOT_FOUND` warning is **not** emitted.
 */
@WarnUserArgEnumNotFoundMarker(verb = WarnUserArgEnumNotFoundVerb.POST)
internal fun warnUserArgEnumNotFound_target(): String = "ok"

class WarnUserArgEnumNotFoundSample : StringSpec({

    "runtime: declaration-origin enum entry resolves and captures the marker" {
        // Baseline: with a declaration-origin marker, the IR phase preserves the
        // enum entry argument verbatim via deepCopy. The
        // CC_USERARG_ENUM_NOT_FOUND warning is **not** emitted.
        val captured = capturedSources<WarnUserArgEnumNotFoundMarker>()
        captured shouldHaveSize 1
    }
})
