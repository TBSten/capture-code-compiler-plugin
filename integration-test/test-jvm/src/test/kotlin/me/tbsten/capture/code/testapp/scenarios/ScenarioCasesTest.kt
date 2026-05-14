package me.tbsten.capture.code.testapp.scenarios

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.scenarios.ordered.OrderedSnippet
import kotlin.reflect.KClass

// ============================================================================
// マイグレーション宣言の収集 (Flyway / Liquibase 風)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FlywayMigration(
    val version: Int,
    val description: String,
    val source: Source = Source(),
)

@FlywayMigration(version = 1, description = "create users table")
val createUsersTableSql = """CREATE TABLE users (id BIGINT PRIMARY KEY, name TEXT)"""

@FlywayMigration(version = 2, description = "add email column")
val addEmailColumnSql = """ALTER TABLE users ADD COLUMN email TEXT"""

// ============================================================================
// テストフィクスチャの収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TestFixture(
    val name: String,
    val source: Source = Source(),
)

@TestFixture(name = "minimal-user")
fun minimalUserFixture() = "{\"id\":1}"

@TestFixture(name = "full-user")
fun fullUserFixture() = "{\"id\":1,\"name\":\"x\",\"email\":\"y\"}"

// ============================================================================
// ベンチマーク対象の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MicroBenchmark(
    val name: String,
    val iterations: Int = 1000,
    val source: Source = Source(),
)

@MicroBenchmark(name = "sum-1000")
fun benchSumTo1000(): Int = (1..1000).sum()

@MicroBenchmark(name = "sum-1000000", iterations = 100)
fun benchSumToOneMillion(): Int = (1..1_000_000).sum()

// ============================================================================
// GraphQL resolver の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GraphQLResolver(
    val type: String,
    val field: String,
    val source: Source = Source(),
)

@GraphQLResolver(type = "User", field = "name")
fun resolveUserName(): String = "Tsubasa"

@GraphQLResolver(type = "User", field = "email")
fun resolveUserEmail(): String = "tsubasa@example.com"

// ============================================================================
// CLI コマンドのカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CliCommand(
    val name: String,
    val help: String,
    val source: Source = Source(),
)

@CliCommand(name = "build", help = "Build the project")
fun cliBuild() = println("building")

@CliCommand(name = "test", help = "Run tests")
fun cliTest() = println("testing")

@CliCommand(name = "clean", help = "Remove build artifacts")
fun cliClean() = println("cleaning")

// ============================================================================
// ドキュメント用のサンプル収集 (Markdown 生成想定)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DocSample(
    val title: String,
    val source: Source = Source(),
)

@DocSample(title = "Hello World")
fun docSampleHelloWorld() {
    println("Hello, World!")
}

@DocSample(title = "List filtering")
fun docSampleFilterEven() {
    val evens = (1..10).filter { it % 2 == 0 }
    println(evens)
}

// ============================================================================
// DSL 利用例の収集 (式キャプチャ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DslExample(val source: Source = Source())

class HtmlBuilder {
    fun div(block: HtmlBuilder.() -> Unit): HtmlBuilder = apply(block)
    fun p(text: String): HtmlBuilder = this
}

fun htmlPage(block: HtmlBuilder.() -> Unit): HtmlBuilder =
    HtmlBuilder().apply(block)

val examplePage = @DslExample() htmlPage {
    div {
        p("Hello")
        p("World")
    }
}

// ============================================================================
// schema 宣言の収集 (Exposed / SQLDelight 風)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SchemaTable(
    val name: String,
    val source: Source = Source(),
)

@SchemaTable(name = "users")
class UsersTable {
    val id = "id INT PRIMARY KEY"
    val name = "name TEXT NOT NULL"
}

@SchemaTable(name = "posts")
class PostsTable {
    val id = "id INT PRIMARY KEY"
    val userId = "user_id INT NOT NULL"
    val body = "body TEXT"
}

// ============================================================================
// feature flag のカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BooleanFeatureFlag(
    val key: String,
    val defaultEnabled: Boolean,
    val source: Source = Source(),
)

@BooleanFeatureFlag(key = "new-search", defaultEnabled = false)
val newSearchEnabled = false

@BooleanFeatureFlag(key = "dark-mode", defaultEnabled = true)
val darkModeEnabled = true

// ============================================================================
// コードレビュー チェックリスト項目の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ReviewChecklistItem(
    val category: Category,
    val source: Source = Source(),
) {
    enum class Category { Naming, Safety, Performance, Style }
}

@ReviewChecklistItem(category = ReviewChecklistItem.Category.Naming)
fun checkNamingConvention() = Unit

@ReviewChecklistItem(category = ReviewChecklistItem.Category.Safety)
fun checkNullability() = Unit

@ReviewChecklistItem(category = ReviewChecklistItem.Category.Performance)
fun checkCollectionAllocation() = Unit

// ============================================================================
// tutorial step (順序付き) のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TutorialStep(
    val order: Int,
    val title: String,
    val source: Source = Source(),
)

@TutorialStep(order = 1, title = "Install")
fun tutorialInstall() = "Run `brew install foo`"

@TutorialStep(order = 2, title = "Configure")
fun tutorialConfigure() = "Edit ~/.foorc"

@TutorialStep(order = 3, title = "Run")
fun tutorialRun() = "Execute `foo run`"

// ============================================================================
// REST controller (Spring 風) の API カタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class RestEndpoint(
    val method: HttpVerb,
    val path: String,
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
) {
    enum class HttpVerb { GET, POST, PUT, DELETE }
}

class UserRestController {
    @RestEndpoint(method = RestEndpoint.HttpVerb.GET, path = "/users/{id}")
    fun getUser(id: Long): String = "user-$id"

    @RestEndpoint(method = RestEndpoint.HttpVerb.POST, path = "/users")
    fun createUser(body: String): String = "created"
}

// ============================================================================
// validator rule のカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ValidationRule(
    val message: String,
    val source: Source = Source(),
)

@ValidationRule(message = "name must not be blank")
val nameNonBlankRule: (String) -> Boolean = { it.isNotBlank() }

@ValidationRule(message = "email must contain @")
val emailHasAtRule: (String) -> Boolean = { it.contains('@') }

// ============================================================================
// DI binding のカタログ (Koin/Anvil 風)
// ============================================================================
internal interface AppLogger
internal interface AppUserRepository

@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DiBinding(
    val to: KClass<*>,
    val source: Source = Source(),
)

@DiBinding(to = AppLogger::class)
class ConsoleLogger : AppLogger

@DiBinding(to = AppUserRepository::class)
class InMemoryUserRepository : AppUserRepository

// ============================================================================
// 設定キー (config keys) の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ConfigKey(
    val key: String,
    val description: String,
    val source: Source = Source(),
)

@ConfigKey(key = "db.url", description = "JDBC URL")
val dbUrlConfig = "jdbc:postgresql://localhost/test"

@ConfigKey(key = "db.maxConnections", description = "Connection pool size")
val maxConnectionsConfig = 20

@ConfigKey(key = "log.level", description = "Logging level")
val logLevelConfig = "INFO"

// ============================================================================
// ユーザーシナリオ (BDD 風) のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BddScenario(
    val feature: String,
    val scenario: String,
    val source: Source = Source(),
)

@BddScenario(feature = "Login", scenario = "valid credentials")
fun loginWithValidCredentials() {
    // Given a registered user, When they enter correct credentials, Then they are logged in
}

@BddScenario(feature = "Login", scenario = "invalid password")
fun loginWithInvalidPassword() {
    // Given a registered user, When wrong password, Then access denied
}

// ============================================================================
// コード補完用のテンプレート (live template 風) 収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LiveTemplate(
    val abbrev: String,
    val source: Source = Source(),
)

@LiveTemplate(abbrev = "fori")
val forILoopTemplate = "for (i in 0 until N) {\n    \n}"

@LiveTemplate(abbrev = "psvm")
val psvmTemplate = "fun main() {\n    \n}"

// ============================================================================
// enum エントリの宣言キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class EnumSnippet(val source: Source = Source())

@EnumSnippet
enum class HttpStatusCode(val code: Int) {
    OK(200),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500),
}

// ============================================================================
// クラス + その内部メンバ関数 (両方が別 marker でキャプチャ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NestedTypeMarker(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NestedFnMarker(val source: Source = Source())

@NestedTypeMarker
class NestedService {
    @NestedFnMarker
    fun execute() = 42
}

// ============================================================================
// marker annotation 自身を SourceLocation のみで使う (軽量 location 収集)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LocationOnlyMarker(
    val location: SourceLocation = SourceLocation(),
)

@LocationOnlyMarker
fun trackedFnA() {
}

@LocationOnlyMarker
fun trackedFnB() {
}

@LocationOnlyMarker
fun trackedFnC() {
}

// ============================================================================
// バックティック識別子をキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class BacktickIdentifierSnippet(val source: Source = Source())

@BacktickIdentifierSnippet
fun `user can login successfully`() {}

// ============================================================================
// 複数の filler 同時 + 複数 marker + 複数ファイルの統合シナリオ
// 複数ファイル部分は comprehensive/FileB.kt と協調
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ComprehensiveSnippet(
    val tag: String = "default",
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

@ComprehensiveSnippet(tag = "math")
fun comprehensiveAdd(a: Int, b: Int) = a + b

@ComprehensiveSnippet(tag = "math")
val comprehensivePi = 3.14159

class ScenarioCasesTest : StringSpec({

    "マイグレーション宣言の収集" {
        capturedSources<FlywayMigration>() shouldBe listOf(
            FlywayMigration(
                version = 1,
                description = "create users table",
                source = Source(
                    value = "val createUsersTableSql = \"\"\"CREATE TABLE users (id BIGINT PRIMARY KEY, name TEXT)\"\"\"",
                ),
            ),
            FlywayMigration(
                version = 2,
                description = "add email column",
                source = Source(
                    value = "val addEmailColumnSql = \"\"\"ALTER TABLE users ADD COLUMN email TEXT\"\"\"",
                ),
            ),
        )
    }

    "テストフィクスチャの収集" {
        capturedSources<TestFixture>() shouldBe listOf(
            TestFixture(
                name = "minimal-user",
                source = Source(value = "fun minimalUserFixture() = \"{\\\"id\\\":1}\""),
            ),
            TestFixture(
                name = "full-user",
                source = Source(
                    value = "fun fullUserFixture() = \"{\\\"id\\\":1,\\\"name\\\":\\\"x\\\",\\\"email\\\":\\\"y\\\"}\"",
                ),
            ),
        )
    }

    "ベンチマーク対象の収集" {
        capturedSources<MicroBenchmark>() shouldBe listOf(
            MicroBenchmark(
                name = "sum-1000",
                iterations = 1000,
                source = Source(value = "fun benchSumTo1000(): Int = (1..1000).sum()"),
            ),
            MicroBenchmark(
                name = "sum-1000000",
                iterations = 100,
                source = Source(value = "fun benchSumToOneMillion(): Int = (1..1_000_000).sum()"),
            ),
        )
    }

    "GraphQL resolver の収集" {
        capturedSources<GraphQLResolver>() shouldBe listOf(
            GraphQLResolver(
                type = "User",
                field = "name",
                source = Source(value = "fun resolveUserName(): String = \"Tsubasa\""),
            ),
            GraphQLResolver(
                type = "User",
                field = "email",
                source = Source(value = "fun resolveUserEmail(): String = \"tsubasa@example.com\""),
            ),
        )
    }

    "CLI コマンドのカタログ" {
        capturedSources<CliCommand>() shouldBe listOf(
            CliCommand(
                name = "build",
                help = "Build the project",
                source = Source(value = "fun cliBuild() = println(\"building\")"),
            ),
            CliCommand(
                name = "test",
                help = "Run tests",
                source = Source(value = "fun cliTest() = println(\"testing\")"),
            ),
            CliCommand(
                name = "clean",
                help = "Remove build artifacts",
                source = Source(value = "fun cliClean() = println(\"cleaning\")"),
            ),
        )
    }

    "ドキュメント用のサンプル収集" {
        capturedSources<DocSample>() shouldBe listOf(
            DocSample(
                title = "Hello World",
                source = Source(value = "fun docSampleHelloWorld() {\n    println(\"Hello, World!\")\n}"),
            ),
            DocSample(
                title = "List filtering",
                source = Source(
                    value = "fun docSampleFilterEven() {\n    val evens = (1..10).filter { it % 2 == 0 }\n    println(evens)\n}",
                ),
            ),
        )
    }

    "DSL 利用例の収集" {
        capturedSources<DslExample>() shouldBe listOf(
            DslExample(
                source = Source(
                    value = "htmlPage {\n    div {\n        p(\"Hello\")\n        p(\"World\")\n    }\n}",
                ),
            ),
        )
    }

    "schema 宣言の収集" {
        capturedSources<SchemaTable>() shouldBe listOf(
            SchemaTable(
                name = "users",
                source = Source(
                    value = "class UsersTable {\n    val id = \"id INT PRIMARY KEY\"\n    val name = \"name TEXT NOT NULL\"\n}",
                ),
            ),
            SchemaTable(
                name = "posts",
                source = Source(
                    value = "class PostsTable {\n    val id = \"id INT PRIMARY KEY\"\n    val userId = \"user_id INT NOT NULL\"\n    val body = \"body TEXT\"\n}",
                ),
            ),
        )
    }

    "feature flag のカタログ" {
        capturedSources<BooleanFeatureFlag>() shouldBe listOf(
            BooleanFeatureFlag(
                key = "new-search",
                defaultEnabled = false,
                source = Source(value = "val newSearchEnabled = false"),
            ),
            BooleanFeatureFlag(
                key = "dark-mode",
                defaultEnabled = true,
                source = Source(value = "val darkModeEnabled = true"),
            ),
        )
    }

    "コードレビュー チェックリスト項目の収集" {
        capturedSources<ReviewChecklistItem>() shouldBe listOf(
            ReviewChecklistItem(
                category = ReviewChecklistItem.Category.Naming,
                source = Source(value = "fun checkNamingConvention() = Unit"),
            ),
            ReviewChecklistItem(
                category = ReviewChecklistItem.Category.Safety,
                source = Source(value = "fun checkNullability() = Unit"),
            ),
            ReviewChecklistItem(
                category = ReviewChecklistItem.Category.Performance,
                source = Source(value = "fun checkCollectionAllocation() = Unit"),
            ),
        )
    }

    "tutorial step (順序付き) のキャプチャ" {
        capturedSources<TutorialStep>() shouldBe listOf(
            TutorialStep(
                order = 1,
                title = "Install",
                source = Source(value = "fun tutorialInstall() = \"Run `brew install foo`\""),
            ),
            TutorialStep(
                order = 2,
                title = "Configure",
                source = Source(value = "fun tutorialConfigure() = \"Edit ~/.foorc\""),
            ),
            TutorialStep(
                order = 3,
                title = "Run",
                source = Source(value = "fun tutorialRun() = \"Execute `foo run`\""),
            ),
        )
    }

    "REST controller (Spring 風) の API カタログ" {
        val captured = capturedSources<RestEndpoint>()
        captured.size shouldBe 2
        captured[0].method shouldBe RestEndpoint.HttpVerb.GET
        captured[0].path shouldBe "/users/{id}"
        captured[1].method shouldBe RestEndpoint.HttpVerb.POST
        captured[1].path shouldBe "/users"
    }

    "validator rule のカタログ" {
        capturedSources<ValidationRule>() shouldBe listOf(
            ValidationRule(
                message = "name must not be blank",
                source = Source(
                    value = "val nameNonBlankRule: (String) -> Boolean = { it.isNotBlank() }",
                ),
            ),
            ValidationRule(
                message = "email must contain @",
                source = Source(
                    value = "val emailHasAtRule: (String) -> Boolean = { it.contains('@') }",
                ),
            ),
        )
    }

    "DI binding のカタログ" {
        capturedSources<DiBinding>() shouldBe listOf(
            DiBinding(
                to = AppLogger::class,
                source = Source(value = "class ConsoleLogger : AppLogger"),
            ),
            DiBinding(
                to = AppUserRepository::class,
                source = Source(value = "class InMemoryUserRepository : AppUserRepository"),
            ),
        )
    }

    "設定キー (config keys) の収集" {
        capturedSources<ConfigKey>() shouldBe listOf(
            ConfigKey(
                key = "db.url",
                description = "JDBC URL",
                source = Source(value = "val dbUrlConfig = \"jdbc:postgresql://localhost/test\""),
            ),
            ConfigKey(
                key = "db.maxConnections",
                description = "Connection pool size",
                source = Source(value = "val maxConnectionsConfig = 20"),
            ),
            ConfigKey(
                key = "log.level",
                description = "Logging level",
                source = Source(value = "val logLevelConfig = \"INFO\""),
            ),
        )
    }

    "ユーザーシナリオ (BDD 風) のキャプチャ" {
        capturedSources<BddScenario>() shouldBe listOf(
            BddScenario(
                feature = "Login",
                scenario = "valid credentials",
                source = Source(
                    value = "fun loginWithValidCredentials() {\n    // Given a registered user, When they enter correct credentials, Then they are logged in\n}",
                ),
            ),
            BddScenario(
                feature = "Login",
                scenario = "invalid password",
                source = Source(
                    value = "fun loginWithInvalidPassword() {\n    // Given a registered user, When wrong password, Then access denied\n}",
                ),
            ),
        )
    }

    "コード補完用のテンプレート (live template 風) 収集" {
        capturedSources<LiveTemplate>() shouldBe listOf(
            LiveTemplate(
                abbrev = "fori",
                source = Source(value = "val forILoopTemplate = \"for (i in 0 until N) {\\n    \\n}\""),
            ),
            LiveTemplate(
                abbrev = "psvm",
                source = Source(value = "val psvmTemplate = \"fun main() {\\n    \\n}\""),
            ),
        )
    }

    "enum エントリの宣言キャプチャ" {
        capturedSources<EnumSnippet>() shouldBe listOf(
            EnumSnippet(
                source = Source(
                    value = "enum class HttpStatusCode(val code: Int) {\n    OK(200),\n    NOT_FOUND(404),\n    INTERNAL_SERVER_ERROR(500),\n}",
                ),
            ),
        )
    }

    "クラス + その内部メンバ関数 (両方が別 marker でキャプチャ - Type 側)" {
        capturedSources<NestedTypeMarker>() shouldBe listOf(
            NestedTypeMarker(
                source = Source(value = "class NestedService {\n    @NestedFnMarker\n    fun execute() = 42\n}"),
            ),
        )
    }

    "クラス + その内部メンバ関数 (両方が別 marker でキャプチャ - Fn 側)" {
        capturedSources<NestedFnMarker>() shouldBe listOf(
            NestedFnMarker(source = Source(value = "fun execute() = 42")),
        )
    }

    "marker annotation 自身を SourceLocation のみで使う (軽量 location 収集)" {
        val captured = capturedSources<LocationOnlyMarker>()
        captured.size shouldBe 3
        captured.forEach { it.location.packageName shouldBe "me.tbsten.capture.code.testapp.scenarios" }
    }

    "順序が保たれることの検証 (interleaved な順序)" {
        // marker と sites は ordered/FileA.kt / ordered/FileB.kt / ordered/FileA2.kt に配置
        val captured = capturedSources<OrderedSnippet>()
        captured.size shouldBe 4
        captured shouldBe listOf(
            OrderedSnippet(source = Source(value = "fun orderedA1() {}")),
            OrderedSnippet(source = Source(value = "fun orderedA2() {}")),
            OrderedSnippet(source = Source(value = "fun orderedA3() {}")),
            OrderedSnippet(source = Source(value = "fun orderedB1() {}")),
        )
    }

    "バックティック識別子をキャプチャ" {
        capturedSources<BacktickIdentifierSnippet>() shouldBe listOf(
            BacktickIdentifierSnippet(source = Source(value = "fun `user can login successfully`() {}")),
        )
    }

    "複数の filler 同時 + 複数 marker + 複数ファイルの統合シナリオ" {
        // fileA: comprehensiveAdd, comprehensivePi (こちらのファイル) — tag="math"
        // fileB: comprehensiveGreet — tag="default" (comprehensive/FileB.kt)
        val captured = capturedSources<ComprehensiveSnippet>()
        captured.size shouldBe 3
    }
})
