package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case24.Snippets_Case24
import me.tbsten.capture.code.testapp.case6.SnippetFile_Case6
import kotlin.reflect.KClass

// ============================================================================
// ケース1: 最小構成 — property を 1 つだけキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case1(val source: Source = Source())

@Snippets_Case1
val case1_greeting = "hello"

// ============================================================================
// ケース2: property + class を同一 marker でキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case2(val source: Source = Source())

@Snippets_Case2
val case2_name = "Tsubasa"

@Snippets_Case2
class Case2_User(val id: Int)

// ============================================================================
// ケース3: object 宣言のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case3(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Case3
object Case3_Singleton {
    val instanceId = 1
}

// ============================================================================
// ケース4: 関数宣言のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case4(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Case4
fun case4_greet(name: String): String = "Hello, $name!"

// ============================================================================
// ケース5: typealias のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case5(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Case5
typealias Case5_UserId = Long

// ============================================================================
// ケース8: filler なしの marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Bench_Case8

@Bench_Case8
fun case8_targetA() {
}

@Bench_Case8
fun case8_targetB() {
}

// ============================================================================
// ケース9: Source のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class JustSource_Case9(val source: Source = Source())

@JustSource_Case9
fun case9_example() = 42

// ============================================================================
// ケース10: SourceLocation のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WhereIs_Case10(val location: SourceLocation = SourceLocation())

@WhereIs_Case10
val case10_flag = true

// ============================================================================
// ケース11: CaptureKind のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KindOnly_Case11(val kind: CaptureKind = CaptureKind())

@KindOnly_Case11
val case11_a = 1

@KindOnly_Case11
class Case11_B

@KindOnly_Case11
fun case11_c() = 3

// ============================================================================
// ケース12: 全 filler を持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FullCapture_Case12(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

@FullCapture_Case12
fun case12_answer() = 42

// ============================================================================
// ケース13: Id 付きマーカー (enum パラメータ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureSample_Case13(
    val id: Id,
    val source: Source = Source(),
) {
    enum class Id { Sample1, Sample2 }
}

@CaptureSample_Case13(id = CaptureSample_Case13.Id.Sample1)
fun case13_sample1() {
    println("This is sample 1")
}

@CaptureSample_Case13(id = CaptureSample_Case13.Id.Sample2)
fun case13_sample2() {
    println("This is sample 2")
}

// ============================================================================
// ケース14: String パラメータを持つ marker (label)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Labeled_Case14(
    val label: String,
    val source: Source = Source(),
)

@Labeled_Case14(label = "primary")
fun case14_handlePrimary() {
}

@Labeled_Case14(label = "secondary")
fun case14_handleSecondary() {
}

// ============================================================================
// ケース15: Int パラメータ (priority)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Prioritized_Case15(
    val priority: Int,
    val source: Source = Source(),
)

@Prioritized_Case15(priority = 100)
fun case15_first() {
}

@Prioritized_Case15(priority = 50)
fun case15_second() {
}

// ============================================================================
// ケース16: Boolean パラメータ (experimental flag)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FeatureFlag_Case16(
    val experimental: Boolean = false,
    val source: Source = Source(),
)

@FeatureFlag_Case16(experimental = true)
fun case16_newBehavior() = "new"

@FeatureFlag_Case16
fun case16_stableBehavior() = "stable"

// ============================================================================
// ケース17: KClass パラメータ
// ============================================================================
internal interface Case17_Service

@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BoundTo_Case17(
    val target: KClass<*>,
    val source: Source = Source(),
)

@BoundTo_Case17(target = Case17_Service::class)
class Case17_ServiceImpl : Case17_Service

// ============================================================================
// ケース18: 配列パラメータ (tags)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Tagged_Case18(
    val tags: Array<String>,
    val source: Source = Source(),
)

@Tagged_Case18(tags = ["fast", "unit"])
fun case18_unitTest() {
}

@Tagged_Case18(tags = ["slow", "integration", "db"])
fun case18_integrationTest() {
}

// ============================================================================
// ケース19: enum 配列パラメータ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Permissions_Case19(
    val roles: Array<Role>,
    val source: Source = Source(),
) {
    enum class Role { Admin, User, Guest }
}

@Permissions_Case19(roles = [Permissions_Case19.Role.Admin, Permissions_Case19.Role.User])
fun case19_adminAndUserEndpoint() {
}

// ============================================================================
// ケース20: Default 値が使われるパラメータ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WithDefaults_Case20(
    val label: String = "untitled",
    val priority: Int = 0,
    val source: Source = Source(),
)

@WithDefaults_Case20
fun case20_noOverrides() {
}

@WithDefaults_Case20(label = "custom")
fun case20_onlyLabel() {
}

@WithDefaults_Case20(priority = 9)
fun case20_onlyPriority() {
}

// ============================================================================
// ケース21: 2 つの marker を同じ宣言に付ける
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Foo_Case21(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Bar_Case21(val source: Source = Source())

@Foo_Case21
@Bar_Case21
fun case21_doubleMarked() = 42

// ============================================================================
// ケース22: 別 marker は混在しても独立
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Alpha_Case22(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Beta_Case22(val source: Source = Source())

@Alpha_Case22
fun case22_alphaOne() {}

@Beta_Case22
fun case22_betaOne() {}

@Alpha_Case22
fun case22_alphaTwo() {}

// ============================================================================
// ケース23: 同じ marker で複数サイト
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case23(val source: Source = Source())

@Snippets_Case23
val case23_a = 1

@Snippets_Case23
val case23_b = 2

@Snippets_Case23
val case23_c = 3

@Snippets_Case23
val case23_d = 4

@Snippets_Case23
val case23_e = 5

// ============================================================================
// ケース25: キャプチャ対象が 0 件
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Unused_Case25(val source: Source = Source())

val case25_a = 1
val case25_b = 2

class BasicCasesTest : StringSpec({

    "ケース1: 最小構成 — property を 1 つだけキャプチャ" {
        capturedSources<Snippets_Case1>() shouldBe listOf(
            Snippets_Case1(source = Source(value = "val case1_greeting = \"hello\"")),
        )
    }

    "ケース2: property + class を同一 marker でキャプチャ" {
        capturedSources<Snippets_Case2>() shouldBe listOf(
            Snippets_Case2(source = Source(value = "val case2_name = \"Tsubasa\"")),
            Snippets_Case2(source = Source(value = "class Case2_User(val id: Int)")),
        )
    }

    "ケース3: object 宣言のキャプチャ" {
        capturedSources<Snippets_Case3>() shouldBe listOf(
            Snippets_Case3(
                source = Source(value = "object Case3_Singleton {\n    val instanceId = 1\n}"),
                kind = CaptureKind(value = CaptureKind.Kind.OBJECT),
            ),
        )
    }

    "ケース4: 関数宣言のキャプチャ" {
        capturedSources<Snippets_Case4>() shouldBe listOf(
            Snippets_Case4(
                source = Source(value = "fun case4_greet(name: String): String = \"Hello, \$name!\""),
                kind = CaptureKind(value = CaptureKind.Kind.FUNCTION),
            ),
        )
    }

    "ケース5: typealias のキャプチャ" {
        capturedSources<Snippets_Case5>() shouldBe listOf(
            Snippets_Case5(
                source = Source(value = "typealias Case5_UserId = Long"),
                kind = CaptureKind(value = CaptureKind.Kind.TYPEALIAS),
            ),
        )
    }

    "ケース6: ファイル全体のキャプチャ (@file:)".config(enabled = false) {
        // marker / file annotation は case6/FileSnippet.kt に配置
        capturedSources<SnippetFile_Case6>() shouldBe listOf(
            SnippetFile_Case6(
                source = Source(value = "val case6_a = 1\nval case6_b = 2"),
                kind = CaptureKind(value = CaptureKind.Kind.FILE),
            ),
        )
    }

    // ケース7 (EXPRESSION) は ExpressionCasesTest.kt 側

    "ケース8: filler なしの marker (annotation インスタンスを取得するだけ)" {
        capturedSources<Bench_Case8>() shouldBe listOf(
            Bench_Case8(),
            Bench_Case8(),
        )
    }

    "ケース9: Source のみを持つ marker" {
        capturedSources<JustSource_Case9>() shouldBe listOf(
            JustSource_Case9(source = Source(value = "fun case9_example() = 42")),
        )
    }

    "ケース10: SourceLocation のみを持つ marker" {
        // location の filePath / 行番号は実行時のファイルレイアウトに依存するので
        // 件数と packageName のみで検証する想定 (本実装が決まったら厳密化)
        val captured = capturedSources<WhereIs_Case10>()
        captured.size shouldBe 1
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "ケース11: CaptureKind のみを持つ marker" {
        capturedSources<KindOnly_Case11>() shouldBe listOf(
            KindOnly_Case11(kind = CaptureKind(value = CaptureKind.Kind.PROPERTY)),
            KindOnly_Case11(kind = CaptureKind(value = CaptureKind.Kind.CLASS)),
            KindOnly_Case11(kind = CaptureKind(value = CaptureKind.Kind.FUNCTION)),
        )
    }

    "ケース12: 全 filler を持つ marker" {
        val captured = capturedSources<FullCapture_Case12>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun case12_answer() = 42")
        captured[0].kind shouldBe CaptureKind(value = CaptureKind.Kind.FUNCTION)
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "ケース13: Id 付きマーカー (enum パラメータ)" {
        capturedSources<CaptureSample_Case13>() shouldBe listOf(
            CaptureSample_Case13(
                id = CaptureSample_Case13.Id.Sample1,
                source = Source(value = "fun case13_sample1() {\n    println(\"This is sample 1\")\n}"),
            ),
            CaptureSample_Case13(
                id = CaptureSample_Case13.Id.Sample2,
                source = Source(value = "fun case13_sample2() {\n    println(\"This is sample 2\")\n}"),
            ),
        )
    }

    "ケース14: String パラメータを持つ marker (label)" {
        capturedSources<Labeled_Case14>() shouldBe listOf(
            Labeled_Case14(
                label = "primary",
                source = Source(value = "fun case14_handlePrimary() {\n}"),
            ),
            Labeled_Case14(
                label = "secondary",
                source = Source(value = "fun case14_handleSecondary() {\n}"),
            ),
        )
    }

    "ケース15: Int パラメータ (priority)" {
        capturedSources<Prioritized_Case15>() shouldBe listOf(
            Prioritized_Case15(priority = 100, source = Source(value = "fun case15_first() {\n}")),
            Prioritized_Case15(priority = 50, source = Source(value = "fun case15_second() {\n}")),
        )
    }

    "ケース16: Boolean パラメータ (experimental flag)" {
        capturedSources<FeatureFlag_Case16>() shouldBe listOf(
            FeatureFlag_Case16(
                experimental = true,
                source = Source(value = "fun case16_newBehavior() = \"new\""),
            ),
            FeatureFlag_Case16(
                experimental = false,
                source = Source(value = "fun case16_stableBehavior() = \"stable\""),
            ),
        )
    }

    "ケース17: KClass パラメータ" {
        capturedSources<BoundTo_Case17>() shouldBe listOf(
            BoundTo_Case17(
                target = Case17_Service::class,
                source = Source(value = "class Case17_ServiceImpl : Case17_Service"),
            ),
        )
    }

    "ケース18: 配列パラメータ (tags)" {
        // Array は equals が reference 比較になるため、要素ごとに検証
        val captured = capturedSources<Tagged_Case18>()
        captured.size shouldBe 2
        captured[0].tags.toList() shouldBe listOf("fast", "unit")
        captured[0].source shouldBe Source(value = "fun case18_unitTest() {\n}")
        captured[1].tags.toList() shouldBe listOf("slow", "integration", "db")
        captured[1].source shouldBe Source(value = "fun case18_integrationTest() {\n}")
    }

    "ケース19: enum 配列パラメータ" {
        val captured = capturedSources<Permissions_Case19>()
        captured.size shouldBe 1
        captured[0].roles.toList() shouldBe listOf(
            Permissions_Case19.Role.Admin,
            Permissions_Case19.Role.User,
        )
        captured[0].source shouldBe Source(value = "fun case19_adminAndUserEndpoint() {\n}")
    }

    "ケース20: Default 値が使われるパラメータ" {
        capturedSources<WithDefaults_Case20>() shouldBe listOf(
            WithDefaults_Case20(
                label = "untitled",
                priority = 0,
                source = Source(value = "fun case20_noOverrides() {\n}"),
            ),
            WithDefaults_Case20(
                label = "custom",
                priority = 0,
                source = Source(value = "fun case20_onlyLabel() {\n}"),
            ),
            WithDefaults_Case20(
                label = "untitled",
                priority = 9,
                source = Source(value = "fun case20_onlyPriority() {\n}"),
            ),
        )
    }

    "ケース21: 2 つの marker を同じ宣言に付ける (Foo 側)" {
        capturedSources<Foo_Case21>() shouldBe listOf(
            Foo_Case21(source = Source(value = "fun case21_doubleMarked() = 42")),
        )
    }

    "ケース21: 2 つの marker を同じ宣言に付ける (Bar 側)" {
        capturedSources<Bar_Case21>() shouldBe listOf(
            Bar_Case21(source = Source(value = "fun case21_doubleMarked() = 42")),
        )
    }

    "ケース22: 別 marker は混在しても独立" {
        capturedSources<Alpha_Case22>() shouldBe listOf(
            Alpha_Case22(source = Source(value = "fun case22_alphaOne() {}")),
            Alpha_Case22(source = Source(value = "fun case22_alphaTwo() {}")),
        )
    }

    "ケース23: 同じ marker で複数サイト" {
        capturedSources<Snippets_Case23>() shouldBe listOf(
            Snippets_Case23(source = Source(value = "val case23_a = 1")),
            Snippets_Case23(source = Source(value = "val case23_b = 2")),
            Snippets_Case23(source = Source(value = "val case23_c = 3")),
            Snippets_Case23(source = Source(value = "val case23_d = 4")),
            Snippets_Case23(source = Source(value = "val case23_e = 5")),
        )
    }

    "ケース24: 複数ファイルにまたがるサイト" {
        // marker / sites は case24/FileA.kt / case24/FileB.kt に配置
        val captured = capturedSources<Snippets_Case24>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "fun case24_fromA() = \"A\"")
        captured[1].source shouldBe Source(value = "fun case24_fromB() = \"B\"")
    }

    "ケース25: キャプチャ対象が 0 件".config(enabled = false) {
        capturedSources<Unused_Case25>() shouldBe emptyList()
    }
})
