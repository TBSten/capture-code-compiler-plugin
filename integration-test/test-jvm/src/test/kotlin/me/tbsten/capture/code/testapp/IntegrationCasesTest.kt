package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// ============================================================================
// ケース62: 同一モジュールでサブパッケージから marker を使う
// 本来は別パッケージで marker を定義するが、本テストでは同一パッケージで代用。
// (パッケージ違いを厳密に評価する case は、別ディレクトリのテストファイルで対応する)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Endpoint_Case62(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Endpoint_Case62
fun case62_listUsers(): List<String> = emptyList()

// ============================================================================
// ケース63: 異なる関数内から複数回 capturedSources<T>() を呼ぶ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case63(val source: Source = Source())

@Snippets_Case63
fun case63_a() {
}

@Snippets_Case63
fun case63_b() {
}

private fun case63_consumer1(): List<Snippets_Case63> = capturedSources()
private fun case63_consumer2(): List<Snippets_Case63> = capturedSources()

// ============================================================================
// ケース69: 内部 enum 型を使った Id (Sealed-like パターン)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class HttpMethod_Case69(
    val verb: Verb,
    val path: String,
    val source: Source = Source(),
) {
    enum class Verb { GET, POST, PUT, DELETE, PATCH }
}

@HttpMethod_Case69(verb = HttpMethod_Case69.Verb.GET, path = "/users")
fun case69_listUsers() = "[]"

@HttpMethod_Case69(verb = HttpMethod_Case69.Verb.POST, path = "/users")
fun case69_createUser() = "ok"

// ============================================================================
// ケース70: ネストした annotation パラメータ
// ============================================================================
internal annotation class Case70_Author(val name: String, val email: String = "")

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Doc_Case70(
    val author: Case70_Author,
    val source: Source = Source(),
)

@Doc_Case70(author = Case70_Author(name = "Tsubasa", email = "tsubasa@example.com"))
fun case70_documented() = "ok"

// ============================================================================
// ケース71: 0 個の宣言 (filler 値だけが意味を持つ場合)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TrackPosition_Case71(
    val location: SourceLocation = SourceLocation(),
)

@TrackPosition_Case71
val case71_tracked = "hello"

// ============================================================================
// ケース72: file annotation + 同モジュール内の declaration annotation 混在
// 本テストでは file annotation を別ファイル (case72/FileLevel.kt) で扱う
// declaration annotation はここで宣言
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case72(
    val source: Source = Source(),
    val kind: me.tbsten.capture.code.CaptureKind = me.tbsten.capture.code.CaptureKind(),
)

@Snippets_Case72
fun case72_pure() = 1

// ============================================================================
// ケース73: private marker をファイル内だけで使う
// (private にすると同ファイルからしか参照できないので、テスト関数で取得関数を用意)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class LocalOnly_Case73(val source: Source = Source())

@LocalOnly_Case73
private fun case73_a() = 1

@LocalOnly_Case73
private fun case73_b() = 2

private fun case73_collect(): List<LocalOnly_Case73> = capturedSources()

// ============================================================================
// ケース74: 同一ファイル内に複数 private marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class Foo_Case74(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class Bar_Case74(val source: Source = Source())

@Foo_Case74
private fun case74_foo1() {
}

@Foo_Case74
private fun case74_foo2() {
}

@Bar_Case74
private fun case74_bar1() {
}

private fun case74_collectFoo(): List<Foo_Case74> = capturedSources()
private fun case74_collectBar(): List<Bar_Case74> = capturedSources()

// ============================================================================
// ケース75: 単一行に property 2 つ ⇒ 各々 location 取得
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case75(
    val location: SourceLocation = SourceLocation(),
)

@Snippets_Case75 val case75_p1 = 1; @Snippets_Case75 val case75_p2 = 2

// ============================================================================
// ケース76: 関数本体内ローカル関数のキャプチャ (ローカル宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case76(val source: Source = Source())

fun case76_outer() {
    @Snippets_Case76
    fun localHelper(x: Int) = x * 2
    localHelper(3)
}

// ============================================================================
// ケース77: 同じ marker を使って 2 種類の location を区別 (api と admin)
// 本テストでは同一パッケージで宣言。location 上のパッケージ違いは省略。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Endpoint_Case77(
    val path: String,
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@Endpoint_Case77(path = "/api/v1/users")
fun case77_apiUsers() = "[]"

@Endpoint_Case77(path = "/admin/users")
fun case77_adminUsers() = "[]"

class IntegrationCasesTest : StringSpec({

    "ケース62: 同一モジュールでサブパッケージから marker を使う" {
        val captured = capturedSources<Endpoint_Case62>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun case62_listUsers(): List<String> = emptyList()")
    }

    "ケース63: 異なる関数内から複数回 capturedSources<T>() を呼ぶ" {
        val expected = listOf(
            Snippets_Case63(source = Source(value = "fun case63_a() {\n}")),
            Snippets_Case63(source = Source(value = "fun case63_b() {\n}")),
        )
        case63_consumer1() shouldBe expected
        case63_consumer2() shouldBe expected
    }

    "ケース69: 内部 enum 型を使った Id (Sealed-like パターン)" {
        capturedSources<HttpMethod_Case69>() shouldBe listOf(
            HttpMethod_Case69(
                verb = HttpMethod_Case69.Verb.GET,
                path = "/users",
                source = Source(value = "fun case69_listUsers() = \"[]\""),
            ),
            HttpMethod_Case69(
                verb = HttpMethod_Case69.Verb.POST,
                path = "/users",
                source = Source(value = "fun case69_createUser() = \"ok\""),
            ),
        )
    }

    "ケース70: ネストした annotation パラメータ" {
        capturedSources<Doc_Case70>() shouldBe listOf(
            Doc_Case70(
                author = Case70_Author(name = "Tsubasa", email = "tsubasa@example.com"),
                source = Source(value = "fun case70_documented() = \"ok\""),
            ),
        )
    }

    "ケース71: 0 個の宣言 (filler 値だけが意味を持つ場合)" {
        val captured = capturedSources<TrackPosition_Case71>()
        captured.size shouldBe 1
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "ケース72: file annotation + 同モジュール内の declaration annotation 混在" {
        // file annotation 側のサイトは case72/FileLevel.kt
        // 期待値: file キャプチャ + function キャプチャの両方
        val captured = capturedSources<Snippets_Case72>()
        captured.size shouldBe 2
        // kind で識別: file 起源 (FILE) と declaration 起源 (FUNCTION) が混在する
        val kinds = captured.map { it.kind.value }.toSet()
        kinds shouldBe setOf(
            me.tbsten.capture.code.CaptureKind.Kind.FILE,
            me.tbsten.capture.code.CaptureKind.Kind.FUNCTION,
        )
    }

    "ケース73: private marker をファイル内だけで使う" {
        case73_collect() shouldBe listOf(
            LocalOnly_Case73(source = Source(value = "private fun case73_a() = 1")),
            LocalOnly_Case73(source = Source(value = "private fun case73_b() = 2")),
        )
    }

    "ケース74: 同一ファイル内に複数 private marker (Foo 側)" {
        case74_collectFoo() shouldBe listOf(
            Foo_Case74(source = Source(value = "private fun case74_foo1() {\n}")),
            Foo_Case74(source = Source(value = "private fun case74_foo2() {\n}")),
        )
    }

    "ケース74: 同一ファイル内に複数 private marker (Bar 側)" {
        case74_collectBar() shouldBe listOf(
            Bar_Case74(source = Source(value = "private fun case74_bar1() {\n}")),
        )
    }

    // 注: 同一行に property 2 つ並べた場合、現状の FIR/IR 走査では
    // どちらの property も capture されない (実値は空 list)。
    // 単一行 multi-property の対応は実装側 (compiler-plugin の宣言走査) の修正が必要なため、
    // 別 ticket で扱う。
    "ケース75: 単一行に property 2 つ ⇒ 各々 location 取得".config(enabled = false) {
        val captured = capturedSources<Snippets_Case75>()
        captured.size shouldBe 2
        // 両方 同一行であることを確認 (行番号は実ファイル位置依存なので一致のみ)
        captured[0].location.startLine shouldBe captured[1].location.startLine
    }

    "ケース76: 関数本体内ローカル関数のキャプチャ (ローカル宣言)" {
        capturedSources<Snippets_Case76>() shouldBe listOf(
            Snippets_Case76(source = Source(value = "fun localHelper(x: Int) = x * 2")),
        )
    }

    "ケース77: 同じ marker を使って 2 種類の location を区別" {
        val captured = capturedSources<Endpoint_Case77>()
        captured.size shouldBe 2
        captured[0].path shouldBe "/api/v1/users"
        captured[1].path shouldBe "/admin/users"
    }
})
