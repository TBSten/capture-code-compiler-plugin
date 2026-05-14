package me.tbsten.capture.code.testapp.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources

// ============================================================================
// 同一モジュールでサブパッケージから marker を使う
// 本来は別パッケージで marker を定義するが、本テストでは同一パッケージで代用。
// (パッケージ違いを厳密に評価する case は、別ディレクトリのテストファイルで対応する)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SubpackageEndpoint(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@SubpackageEndpoint
fun subpackageListUsers(): List<String> = emptyList()

// ============================================================================
// 異なる関数内から複数回 capturedSources<T>() を呼ぶ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MultiCallSnippet(val source: Source = Source())

@MultiCallSnippet
fun multiCallA() {
}

@MultiCallSnippet
fun multiCallB() {
}

private fun multiCallConsumer1(): List<MultiCallSnippet> = capturedSources()
private fun multiCallConsumer2(): List<MultiCallSnippet> = capturedSources()

// ============================================================================
// 内部 enum 型を使った Id (Sealed-like パターン)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class HttpMethodMarker(
    val verb: Verb,
    val path: String,
    val source: Source = Source(),
) {
    enum class Verb { GET, POST, PUT, DELETE, PATCH }
}

@HttpMethodMarker(verb = HttpMethodMarker.Verb.GET, path = "/users")
fun httpListUsers() = "[]"

@HttpMethodMarker(verb = HttpMethodMarker.Verb.POST, path = "/users")
fun httpCreateUser() = "ok"

// ============================================================================
// ネストした annotation パラメータ
// ============================================================================
internal annotation class DocAuthor(val name: String, val email: String = "")

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DocMarker(
    val author: DocAuthor,
    val source: Source = Source(),
)

@DocMarker(author = DocAuthor(name = "Tsubasa", email = "tsubasa@example.com"))
fun documentedFn() = "ok"

// ============================================================================
// 0 個の宣言 (filler 値だけが意味を持つ場合)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TrackPositionMarker(
    val location: SourceLocation = SourceLocation(),
)

@TrackPositionMarker
val trackedValue = "hello"

// ============================================================================
// file annotation + 同モジュール内の declaration annotation 混在
// 本テストでは file annotation を別ファイル (filescope/FileLevel.kt) で扱う
// declaration annotation はここで宣言
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FileAndFunctionSnippet(
    val source: Source = Source(),
    val kind: me.tbsten.capture.code.CaptureKind = me.tbsten.capture.code.CaptureKind(),
)

@FileAndFunctionSnippet
fun fileAndFunctionPure() = 1

// ============================================================================
// private marker をファイル内だけで使う
// (private にすると同ファイルからしか参照できないので、テスト関数で取得関数を用意)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class FileScopedPrivateSnippet(val source: Source = Source())

@FileScopedPrivateSnippet
private fun fileScopedA() = 1

@FileScopedPrivateSnippet
private fun fileScopedB() = 2

private fun fileScopedCollect(): List<FileScopedPrivateSnippet> = capturedSources()

// ============================================================================
// 同一ファイル内に複数 private marker
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class LocalFoo(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
private annotation class LocalBar(val source: Source = Source())

@LocalFoo
private fun localFoo1() {
}

@LocalFoo
private fun localFoo2() {
}

@LocalBar
private fun localBar1() {
}

private fun collectLocalFoo(): List<LocalFoo> = capturedSources()
private fun collectLocalBar(): List<LocalBar> = capturedSources()

// ============================================================================
// 単一行に property 2 つ ⇒ 各々 location 取得
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SingleLineMultiPropertySnippet(
    val location: SourceLocation = SourceLocation(),
)

@SingleLineMultiPropertySnippet val singleLineP1 = 1; @SingleLineMultiPropertySnippet val singleLineP2 = 2

// ============================================================================
// 関数本体内ローカル関数のキャプチャ (ローカル宣言)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LocalFunctionSnippet(val source: Source = Source())

fun localFunctionOuter() {
    @LocalFunctionSnippet
    fun localHelper(x: Int) = x * 2
    localHelper(3)
}

// ============================================================================
// 同じ marker を使って 2 種類の location を区別 (api と admin)
// 本テストでは同一パッケージで宣言。location 上のパッケージ違いは省略。
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LocationDistinctEndpoint(
    val path: String,
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
)

@LocationDistinctEndpoint(path = "/api/v1/users")
fun apiUsers() = "[]"

@LocationDistinctEndpoint(path = "/admin/users")
fun adminUsers() = "[]"

class IntegrationCasesTest : StringSpec({

    "同一モジュールでサブパッケージから marker を使う" {
        val captured = capturedSources<SubpackageEndpoint>()
        captured.size shouldBe 1
        captured[0].source shouldBe Source(value = "fun subpackageListUsers(): List<String> = emptyList()")
    }

    "異なる関数内から複数回 capturedSources<T>() を呼ぶ" {
        val expected = listOf(
            MultiCallSnippet(source = Source(value = "fun multiCallA() {\n}")),
            MultiCallSnippet(source = Source(value = "fun multiCallB() {\n}")),
        )
        multiCallConsumer1() shouldBe expected
        multiCallConsumer2() shouldBe expected
    }

    "内部 enum 型を使った Id (Sealed-like パターン)" {
        capturedSources<HttpMethodMarker>() shouldBe listOf(
            HttpMethodMarker(
                verb = HttpMethodMarker.Verb.GET,
                path = "/users",
                source = Source(value = "fun httpListUsers() = \"[]\""),
            ),
            HttpMethodMarker(
                verb = HttpMethodMarker.Verb.POST,
                path = "/users",
                source = Source(value = "fun httpCreateUser() = \"ok\""),
            ),
        )
    }

    "ネストした annotation パラメータ" {
        capturedSources<DocMarker>() shouldBe listOf(
            DocMarker(
                author = DocAuthor(name = "Tsubasa", email = "tsubasa@example.com"),
                source = Source(value = "fun documentedFn() = \"ok\""),
            ),
        )
    }

    "0 個の宣言 (filler 値だけが意味を持つ場合)" {
        val captured = capturedSources<TrackPositionMarker>()
        captured.size shouldBe 1
        captured[0].location.packageName shouldBe "me.tbsten.capture.code.testapp"
    }

    "file annotation + 同モジュール内の declaration annotation 混在" {
        // file annotation 側のサイトは filescope/FileLevel.kt
        // 期待値: file キャプチャ + function キャプチャの両方
        val captured = capturedSources<FileAndFunctionSnippet>()
        captured.size shouldBe 2
        // kind で識別: file 起源 (FILE) と declaration 起源 (FUNCTION) が混在する
        val kinds = captured.map { it.kind.value }.toSet()
        kinds shouldBe setOf(
            me.tbsten.capture.code.CaptureKind.Kind.FILE,
            me.tbsten.capture.code.CaptureKind.Kind.FUNCTION,
        )
    }

    "private marker をファイル内だけで使う" {
        fileScopedCollect() shouldBe listOf(
            FileScopedPrivateSnippet(source = Source(value = "private fun fileScopedA() = 1")),
            FileScopedPrivateSnippet(source = Source(value = "private fun fileScopedB() = 2")),
        )
    }

    "同一ファイル内に複数 private marker (Foo 側)" {
        collectLocalFoo() shouldBe listOf(
            LocalFoo(source = Source(value = "private fun localFoo1() {\n}")),
            LocalFoo(source = Source(value = "private fun localFoo2() {\n}")),
        )
    }

    "同一ファイル内に複数 private marker (Bar 側)" {
        collectLocalBar() shouldBe listOf(
            LocalBar(source = Source(value = "private fun localBar1() {\n}")),
        )
    }

    // 同一行 multi-property 対応 (token ベース skipLeadingAnnotationLines による実装)。
    // 1 番目の property は IR の endOffset が `;` を含むため source に `;` が残るが、 capture は成立する。
    "単一行に property 2 つ ⇒ 各々 location 取得" {
        val captured = capturedSources<SingleLineMultiPropertySnippet>()
        captured.size shouldBe 2
        // 両方 同一行であることを確認 (行番号は実ファイル位置依存なので一致のみ)
        captured[0].location.startLine shouldBe captured[1].location.startLine
    }

    "関数本体内ローカル関数のキャプチャ (ローカル宣言)" {
        capturedSources<LocalFunctionSnippet>() shouldBe listOf(
            LocalFunctionSnippet(source = Source(value = "fun localHelper(x: Int) = x * 2")),
        )
    }

    "同じ marker を使って 2 種類の location を区別" {
        val captured = capturedSources<LocationDistinctEndpoint>()
        captured.size shouldBe 2
        captured[0].path shouldBe "/api/v1/users"
        captured[1].path shouldBe "/admin/users"
    }
})
