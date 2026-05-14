package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.crossfile.Snippets_CrossFile
import me.tbsten.capture.code.testapp.wholefile.SnippetFile_WholeFile
import kotlin.reflect.KClass

// ============================================================================
// 最小構成 — property を 1 つだけキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Greeting(val source: Source = Source())

@Snippets_Greeting
val greeting = "hello"

// ============================================================================
// property + class を同一 marker でキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_PropertyAndClass(val source: Source = Source())

@Snippets_PropertyAndClass
val userName = "Tsubasa"

@Snippets_PropertyAndClass
class Snip_User(val id: Int)

// ============================================================================
// object 宣言のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Object(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Object
object SingletonSnippet {
    val instanceId = 1
}

// ============================================================================
// 関数宣言のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Function(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Function
fun greetFn(name: String): String = "Hello, $name!"

// ============================================================================
// typealias のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_TypeAlias(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_TypeAlias
typealias UserIdAlias = Long

// ============================================================================
// filler なしの marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Bench_NoFiller

@Bench_NoFiller
fun noFillerTargetA() {
}

@Bench_NoFiller
fun noFillerTargetB() {
}

// ============================================================================
// Source のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class JustSource_Marker(val source: Source = Source())

@JustSource_Marker
fun sourceOnlyExample() = 42

// ============================================================================
// SourceLocation のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WhereIs_Marker(val location: SourceLocation = SourceLocation())

@WhereIs_Marker
val locationOnlyFlag = true

// ============================================================================
// CaptureKind のみを持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class KindOnly_Marker(val kind: CaptureKind = CaptureKind())

@KindOnly_Marker
val kindOnlyA = 1

@KindOnly_Marker
class KindOnlyB

@KindOnly_Marker
fun kindOnlyC() = 3

// ============================================================================
// 全 filler を持つ marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FullCapture_Marker(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

@FullCapture_Marker
fun fullCaptureAnswer() = 42

// ============================================================================
// Id 付きマーカー (enum パラメータ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureSample_EnumParam(
    val id: Id,
    val source: Source = Source(),
) {
    enum class Id { Sample1, Sample2 }
}

@CaptureSample_EnumParam(id = CaptureSample_EnumParam.Id.Sample1)
fun enumSample1() {
    println("This is sample 1")
}

@CaptureSample_EnumParam(id = CaptureSample_EnumParam.Id.Sample2)
fun enumSample2() {
    println("This is sample 2")
}

// ============================================================================
// String パラメータを持つ marker (label)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Labeled_Marker(
    val label: String,
    val source: Source = Source(),
)

@Labeled_Marker(label = "primary")
fun labeledPrimary() {
}

@Labeled_Marker(label = "secondary")
fun labeledSecondary() {
}

// ============================================================================
// Int パラメータ (priority)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Prioritized_Marker(
    val priority: Int,
    val source: Source = Source(),
)

@Prioritized_Marker(priority = 100)
fun prioritizedFirst() {
}

@Prioritized_Marker(priority = 50)
fun prioritizedSecond() {
}

// ============================================================================
// Boolean パラメータ (experimental flag)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FeatureFlag_Marker(
    val experimental: Boolean = false,
    val source: Source = Source(),
)

@FeatureFlag_Marker(experimental = true)
fun featureNewBehavior() = "new"

@FeatureFlag_Marker
fun featureStableBehavior() = "stable"

// ============================================================================
// KClass パラメータ
// ============================================================================
internal interface BindingService

@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BoundTo_Marker(
    val target: KClass<*>,
    val source: Source = Source(),
)

@BoundTo_Marker(target = BindingService::class)
class BindingServiceImpl : BindingService

// ============================================================================
// 配列パラメータ (tags)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Tagged_Marker(
    val tags: Array<String>,
    val source: Source = Source(),
)

@Tagged_Marker(tags = ["fast", "unit"])
fun taggedUnitTest() {
}

@Tagged_Marker(tags = ["slow", "integration", "db"])
fun taggedIntegrationTest() {
}

// ============================================================================
// enum 配列パラメータ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Permissions_Marker(
    val roles: Array<Role>,
    val source: Source = Source(),
) {
    enum class Role { Admin, User, Guest }
}

@Permissions_Marker(roles = [Permissions_Marker.Role.Admin, Permissions_Marker.Role.User])
fun permissionsAdminAndUserEndpoint() {
}

// ============================================================================
// Default 値が使われるパラメータ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class WithDefaults_Marker(
    val label: String = "untitled",
    val priority: Int = 0,
    val source: Source = Source(),
)

@WithDefaults_Marker
fun defaultsNoOverrides() {
}

@WithDefaults_Marker(label = "custom")
fun defaultsOnlyLabel() {
}

@WithDefaults_Marker(priority = 9)
fun defaultsOnlyPriority() {
}

// ============================================================================
// 2 つの marker を同じ宣言に付ける
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Foo_Marker(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Bar_Marker(val source: Source = Source())

@Foo_Marker
@Bar_Marker
fun doubleMarked() = 42

// ============================================================================
// 別 marker は混在しても独立
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Alpha_Marker(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Beta_Marker(val source: Source = Source())

@Alpha_Marker
fun independentAlphaOne() {}

@Beta_Marker
fun independentBetaOne() {}

@Alpha_Marker
fun independentAlphaTwo() {}

// ============================================================================
// 同じ marker で複数サイト
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_MultiSite(val source: Source = Source())

@Snippets_MultiSite
val multiSiteA = 1

@Snippets_MultiSite
val multiSiteB = 2

@Snippets_MultiSite
val multiSiteC = 3

@Snippets_MultiSite
val multiSiteD = 4

@Snippets_MultiSite
val multiSiteE = 5

// ============================================================================
// キャプチャ対象が 0 件
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Unused_EmptyMarker(val source: Source = Source())

val unusedA = 1
val unusedB = 2

class BasicCasesTest : StringSpec({

    "最小構成 — property を 1 つだけキャプチャ" {
        capturedSources<Snippets_Greeting>() shouldBe listOf(
            Snippets_Greeting(source = Source(value = "val greeting = \"hello\"")),
        )
    }

    "property + class を同一 marker でキャプチャ" {
        capturedSources<Snippets_PropertyAndClass>() shouldBe listOf(
            Snippets_PropertyAndClass(source = Source(value = "val userName = \"Tsubasa\"")),
            Snippets_PropertyAndClass(source = Source(value = "class Snip_User(val id: Int)")),
        )
    }

    "object 宣言のキャプチャ" {
        capturedSources<Snippets_Object>() shouldBe listOf(
            Snippets_Object(
                source = Source(value = "object SingletonSnippet {\n    val instanceId = 1\n}"),
                kind = CaptureKind(value = CaptureKind.Kind.OBJECT),
            ),
        )
    }

    "関数宣言のキャプチャ" {
        capturedSources<Snippets_Function>() shouldBe listOf(
            Snippets_Function(
                source = Source(value = "fun greetFn(name: String): String = \"Hello, \$name!\""),
                kind = CaptureKind(value = CaptureKind.Kind.FUNCTION),
            ),
        )
    }

    "typealias のキャプチャ" {
        capturedSources<Snippets_TypeAlias>() shouldBe listOf(
            Snippets_TypeAlias(
                source = Source(value = "typealias UserIdAlias = Long"),
                kind = CaptureKind(value = CaptureKind.Kind.TYPEALIAS),
            ),
        )
    }

    "ファイル全体のキャプチャ (@file:)" {
        // marker / file annotation は wholefile/FileSnippet.kt に配置
        capturedSources<SnippetFile_WholeFile>() shouldBe listOf(
            SnippetFile_WholeFile(
                source = Source(value = "val wholeFileA = 1\nval wholeFileB = 2"),
                kind = CaptureKind(value = CaptureKind.Kind.FILE),
            ),
        )
    }

    // EXPRESSION 用テストは ExpressionCasesTest.kt 側

    "filler なしの marker (annotation インスタンスを取得するだけ)" {
        capturedSources<Bench_NoFiller>() shouldBe listOf(
            Bench_NoFiller(),
            Bench_NoFiller(),
        )
    }

    "Source のみを持つ marker" {
        capturedSources<JustSource_Marker>() shouldBe listOf(
            JustSource_Marker(source = Source(value = "fun sourceOnlyExample() = 42")),
        )
    }

    "SourceLocation のみを持つ marker" {
        // location の filePath / 行番号は実行時のファイルレイアウトに依存するので
        // 件数と packageName のみで検証する想定 (本実装が決まったら厳密化)
        val captured = capturedSources<WhereIs_Marker>()
        captured.size shouldBe 1
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "CaptureKind のみを持つ marker" {
        capturedSources<KindOnly_Marker>() shouldBe listOf(
            KindOnly_Marker(kind = CaptureKind(value = CaptureKind.Kind.PROPERTY)),
            KindOnly_Marker(kind = CaptureKind(value = CaptureKind.Kind.CLASS)),
            KindOnly_Marker(kind = CaptureKind(value = CaptureKind.Kind.FUNCTION)),
        )
    }

    "全 filler を持つ marker" {
        val captured = capturedSources<FullCapture_Marker>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun fullCaptureAnswer() = 42")
        captured[0].kind shouldBe CaptureKind(value = CaptureKind.Kind.FUNCTION)
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "Id 付きマーカー (enum パラメータ)" {
        capturedSources<CaptureSample_EnumParam>() shouldBe listOf(
            CaptureSample_EnumParam(
                id = CaptureSample_EnumParam.Id.Sample1,
                source = Source(value = "fun enumSample1() {\n    println(\"This is sample 1\")\n}"),
            ),
            CaptureSample_EnumParam(
                id = CaptureSample_EnumParam.Id.Sample2,
                source = Source(value = "fun enumSample2() {\n    println(\"This is sample 2\")\n}"),
            ),
        )
    }

    "String パラメータを持つ marker (label)" {
        capturedSources<Labeled_Marker>() shouldBe listOf(
            Labeled_Marker(
                label = "primary",
                source = Source(value = "fun labeledPrimary() {\n}"),
            ),
            Labeled_Marker(
                label = "secondary",
                source = Source(value = "fun labeledSecondary() {\n}"),
            ),
        )
    }

    "Int パラメータ (priority)" {
        capturedSources<Prioritized_Marker>() shouldBe listOf(
            Prioritized_Marker(priority = 100, source = Source(value = "fun prioritizedFirst() {\n}")),
            Prioritized_Marker(priority = 50, source = Source(value = "fun prioritizedSecond() {\n}")),
        )
    }

    "Boolean パラメータ (experimental flag)" {
        capturedSources<FeatureFlag_Marker>() shouldBe listOf(
            FeatureFlag_Marker(
                experimental = true,
                source = Source(value = "fun featureNewBehavior() = \"new\""),
            ),
            FeatureFlag_Marker(
                experimental = false,
                source = Source(value = "fun featureStableBehavior() = \"stable\""),
            ),
        )
    }

    "KClass パラメータ" {
        capturedSources<BoundTo_Marker>() shouldBe listOf(
            BoundTo_Marker(
                target = BindingService::class,
                source = Source(value = "class BindingServiceImpl : BindingService"),
            ),
        )
    }

    "配列パラメータ (tags)" {
        // Array は equals が reference 比較になるため、要素ごとに検証
        val captured = capturedSources<Tagged_Marker>()
        captured.size shouldBe 2
        captured[0].tags.toList() shouldBe listOf("fast", "unit")
        captured[0].source shouldBe Source(value = "fun taggedUnitTest() {\n}")
        captured[1].tags.toList() shouldBe listOf("slow", "integration", "db")
        captured[1].source shouldBe Source(value = "fun taggedIntegrationTest() {\n}")
    }

    "enum 配列パラメータ" {
        val captured = capturedSources<Permissions_Marker>()
        captured.size shouldBe 1
        captured[0].roles.toList() shouldBe listOf(
            Permissions_Marker.Role.Admin,
            Permissions_Marker.Role.User,
        )
        captured[0].source shouldBe Source(value = "fun permissionsAdminAndUserEndpoint() {\n}")
    }

    "Default 値が使われるパラメータ" {
        capturedSources<WithDefaults_Marker>() shouldBe listOf(
            WithDefaults_Marker(
                label = "untitled",
                priority = 0,
                source = Source(value = "fun defaultsNoOverrides() {\n}"),
            ),
            WithDefaults_Marker(
                label = "custom",
                priority = 0,
                source = Source(value = "fun defaultsOnlyLabel() {\n}"),
            ),
            WithDefaults_Marker(
                label = "untitled",
                priority = 9,
                source = Source(value = "fun defaultsOnlyPriority() {\n}"),
            ),
        )
    }

    "2 つの marker を同じ宣言に付ける (Foo 側)" {
        capturedSources<Foo_Marker>() shouldBe listOf(
            Foo_Marker(source = Source(value = "fun doubleMarked() = 42")),
        )
    }

    "2 つの marker を同じ宣言に付ける (Bar 側)" {
        capturedSources<Bar_Marker>() shouldBe listOf(
            Bar_Marker(source = Source(value = "fun doubleMarked() = 42")),
        )
    }

    "別 marker は混在しても独立" {
        capturedSources<Alpha_Marker>() shouldBe listOf(
            Alpha_Marker(source = Source(value = "fun independentAlphaOne() {}")),
            Alpha_Marker(source = Source(value = "fun independentAlphaTwo() {}")),
        )
    }

    "同じ marker で複数サイト" {
        capturedSources<Snippets_MultiSite>() shouldBe listOf(
            Snippets_MultiSite(source = Source(value = "val multiSiteA = 1")),
            Snippets_MultiSite(source = Source(value = "val multiSiteB = 2")),
            Snippets_MultiSite(source = Source(value = "val multiSiteC = 3")),
            Snippets_MultiSite(source = Source(value = "val multiSiteD = 4")),
            Snippets_MultiSite(source = Source(value = "val multiSiteE = 5")),
        )
    }

    "複数ファイルにまたがるサイト" {
        // marker / sites は crossfile/FileA.kt / crossfile/FileB.kt に配置
        val captured = capturedSources<Snippets_CrossFile>()
        captured.size shouldBe 2
        captured[0].source shouldBe Source(value = "fun crossFromA() = \"A\"")
        captured[1].source shouldBe Source(value = "fun crossFromB() = \"B\"")
    }

    "キャプチャ対象が 0 件" {
        capturedSources<Unused_EmptyMarker>() shouldBe emptyList()
    }
})
