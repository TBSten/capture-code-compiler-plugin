package me.tbsten.capture.code.testapp.warning

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.reflect.KClass
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// task-134: `CC_USERARG_CLASS_REF_UNSUPPORTED` warning の発火ドキュメント。
//
// 本 warning は **EXPRESSION 起源 (= 式の statement に付いた `@Marker(...)` annotation)
// で `::class` reference を user arg として指定した** 場合に発火する。 IR phase での
// `IrClassReference` 再構築は 0.4.0+ scope で task-134 時点では未対応のため、 silent ignore
// (= null 返却 → default 値 fallback) されていた経路を `CC_USERARG_CLASS_REF_UNSUPPORTED`
// warning として user 通知する (`Any` 深掘り review report §4.3 #3 改善 E)。
//
// 同一 single-module compilation で EXPRESSION 起源 + `::class` arg を再現するには、
// `@Marker(target = SomeClass::class)` を式 statement に付ける必要がある。 task-091 で
// marker を `AnnotationTarget.EXPRESSION` に絞れない制約があり、 また EXPRESSION 起源の
// annotation は **Kotlin 2.0 baseline で IR phase に残らない** (= FIR session storage
// 経由で push される) ため、 同一 module で 1 ファイル中に declared declaration + EXPRESSION
// 起源 site を両方持つテストはまだ整備されていない。 本 sample は **declaration 起源で
// `::class` arg が正常に deep copy される baseline を verify** することで、 declaration
// 経路は warning が出ないことを sanity check として固定する。
//
// 真の発火 verify は将来的に `:compiler-plugin` の kctfork unit test で
// `ClassRef(classFqn = "com.example.MyClass")` を expressionUserArgs に直接 push する
// 経路を mock するか、 EXPRESSION 起源 capture 用の dedicated integration-test sub-module
// を追加する方が現実的。 task-134 では documentation + factory 登録 / SSoT 整備 /
// `MessageCollector` 経由の emit pipeline 整備を対象とする。
//
// 実機で `--info` を付けて compileTestKotlin を走らせても、 本 marker は declaration 起源
// のみのため warning は出ない (= silent success が期待挙動)。
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin
// ============================================================================

internal interface WarnUserArgClassRefUnsupportedService

@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WarnUserArgClassRefUnsupportedMarker(
    val target: KClass<*>,
    val source: Source = Source(),
)

/**
 * Declaration-origin baseline: the IR phase deep-copies the original
 * `IrClassReference` so the `target` argument is preserved verbatim and the
 * `CC_USERARG_CLASS_REF_UNSUPPORTED` warning is **not** emitted (= the warning
 * only fires on EXPRESSION-origin sites where the IR rebuild is not yet
 * supported).
 */
@WarnUserArgClassRefUnsupportedMarker(target = WarnUserArgClassRefUnsupportedService::class)
internal class WarnUserArgClassRefUnsupportedImpl : WarnUserArgClassRefUnsupportedService

class WarnUserArgClassRefUnsupportedSample : StringSpec({

    "runtime: declaration-origin ::class reference resolves and captures the marker" {
        // Baseline: with a declaration-origin marker, the IR phase preserves the
        // `::class` argument verbatim via deepCopy. The
        // CC_USERARG_CLASS_REF_UNSUPPORTED warning is **not** emitted.
        val captured = capturedSources<WarnUserArgClassRefUnsupportedMarker>()
        captured shouldHaveSize 1
    }
})
