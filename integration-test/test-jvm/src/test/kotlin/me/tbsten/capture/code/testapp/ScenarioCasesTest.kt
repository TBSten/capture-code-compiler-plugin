package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation
import me.tbsten.capture.code.capturedSources
import me.tbsten.capture.code.testapp.case98.Snippets_Case98
import kotlin.reflect.KClass

// ============================================================================
// ケース78: マイグレーション宣言の収集 (Flyway / Liquibase 風)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Migration_Case78(
    val version: Int,
    val description: String,
    val source: Source = Source(),
)

@Migration_Case78(version = 1, description = "create users table")
val case78_v1 = """CREATE TABLE users (id BIGINT PRIMARY KEY, name TEXT)"""

@Migration_Case78(version = 2, description = "add email column")
val case78_v2 = """ALTER TABLE users ADD COLUMN email TEXT"""

// ============================================================================
// ケース79: テストフィクスチャの収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Fixture_Case79(
    val name: String,
    val source: Source = Source(),
)

@Fixture_Case79(name = "minimal-user")
fun case79_minimalUser() = "{\"id\":1}"

@Fixture_Case79(name = "full-user")
fun case79_fullUser() = "{\"id\":1,\"name\":\"x\",\"email\":\"y\"}"

// ============================================================================
// ケース80: ベンチマーク対象の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Benchmark_Case80(
    val name: String,
    val iterations: Int = 1000,
    val source: Source = Source(),
)

@Benchmark_Case80(name = "sum-1000")
fun case80_sum1000(): Int = (1..1000).sum()

@Benchmark_Case80(name = "sum-1000000", iterations = 100)
fun case80_sum1m(): Int = (1..1_000_000).sum()

// ============================================================================
// ケース81: GraphQL resolver の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GraphQLResolver_Case81(
    val type: String,
    val field: String,
    val source: Source = Source(),
)

@GraphQLResolver_Case81(type = "User", field = "name")
fun case81_userName(): String = "Tsubasa"

@GraphQLResolver_Case81(type = "User", field = "email")
fun case81_userEmail(): String = "tsubasa@example.com"

// ============================================================================
// ケース82: CLI コマンドのカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Command_Case82(
    val name: String,
    val help: String,
    val source: Source = Source(),
)

@Command_Case82(name = "build", help = "Build the project")
fun case82_build() = println("building")

@Command_Case82(name = "test", help = "Run tests")
fun case82_test() = println("testing")

@Command_Case82(name = "clean", help = "Remove build artifacts")
fun case82_clean() = println("cleaning")

// ============================================================================
// ケース83: ドキュメント用のサンプル収集 (Markdown 生成想定)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DocSample_Case83(
    val title: String,
    val source: Source = Source(),
)

@DocSample_Case83(title = "Hello World")
fun case83_helloWorld() {
    println("Hello, World!")
}

@DocSample_Case83(title = "List filtering")
fun case83_filterEven() {
    val evens = (1..10).filter { it % 2 == 0 }
    println(evens)
}

// ============================================================================
// ケース84: DSL 利用例の収集 (式キャプチャ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DslExample_Case84(val source: Source = Source())

class Case84_HtmlBuilder {
    fun div(block: Case84_HtmlBuilder.() -> Unit): Case84_HtmlBuilder = apply(block)
    fun p(text: String): Case84_HtmlBuilder = this
}

fun case84_html(block: Case84_HtmlBuilder.() -> Unit): Case84_HtmlBuilder =
    Case84_HtmlBuilder().apply(block)

val case84_page = @DslExample_Case84 case84_html {
    div {
        p("Hello")
        p("World")
    }
}

// ============================================================================
// ケース85: schema 宣言の収集 (Exposed / SQLDelight 風)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Table_Case85(
    val name: String,
    val source: Source = Source(),
)

@Table_Case85(name = "users")
class Case85_UsersTable {
    val id = "id INT PRIMARY KEY"
    val name = "name TEXT NOT NULL"
}

@Table_Case85(name = "posts")
class Case85_PostsTable {
    val id = "id INT PRIMARY KEY"
    val userId = "user_id INT NOT NULL"
    val body = "body TEXT"
}

// ============================================================================
// ケース86: feature flag のカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Flag_Case86(
    val key: String,
    val defaultEnabled: Boolean,
    val source: Source = Source(),
)

@Flag_Case86(key = "new-search", defaultEnabled = false)
val case86_newSearchEnabled = false

@Flag_Case86(key = "dark-mode", defaultEnabled = true)
val case86_darkModeEnabled = true

// ============================================================================
// ケース87: コードレビュー チェックリスト項目の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ChecklistItem_Case87(
    val category: Category,
    val source: Source = Source(),
) {
    enum class Category { Naming, Safety, Performance, Style }
}

@ChecklistItem_Case87(category = ChecklistItem_Case87.Category.Naming)
fun case87_checkNamingConvention() = Unit

@ChecklistItem_Case87(category = ChecklistItem_Case87.Category.Safety)
fun case87_checkNullability() = Unit

@ChecklistItem_Case87(category = ChecklistItem_Case87.Category.Performance)
fun case87_checkCollectionAllocation() = Unit

// ============================================================================
// ケース88: tutorial step (順序付き) のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TutorialStep_Case88(
    val order: Int,
    val title: String,
    val source: Source = Source(),
)

@TutorialStep_Case88(order = 1, title = "Install")
fun case88_step1() = "Run `brew install foo`"

@TutorialStep_Case88(order = 2, title = "Configure")
fun case88_step2() = "Edit ~/.foorc"

@TutorialStep_Case88(order = 3, title = "Run")
fun case88_step3() = "Execute `foo run`"

// ============================================================================
// ケース89: REST controller (Spring 風) の API カタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class RestRoute_Case89(
    val method: HttpVerb,
    val path: String,
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
) {
    enum class HttpVerb { GET, POST, PUT, DELETE }
}

class Case89_UserController {
    @RestRoute_Case89(method = RestRoute_Case89.HttpVerb.GET, path = "/users/{id}")
    fun getUser(id: Long): String = "user-$id"

    @RestRoute_Case89(method = RestRoute_Case89.HttpVerb.POST, path = "/users")
    fun createUser(body: String): String = "created"
}

// ============================================================================
// ケース90: validator rule のカタログ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Rule_Case90(
    val message: String,
    val source: Source = Source(),
)

@Rule_Case90(message = "name must not be blank")
val case90_nameNonBlank: (String) -> Boolean = { it.isNotBlank() }

@Rule_Case90(message = "email must contain @")
val case90_emailHasAt: (String) -> Boolean = { it.contains('@') }

// ============================================================================
// ケース91: DI binding のカタログ (Koin/Anvil 風)
// ============================================================================
internal interface Case91_Logger
internal interface Case91_UserRepository

@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Bind_Case91(
    val to: KClass<*>,
    val source: Source = Source(),
)

@Bind_Case91(to = Case91_Logger::class)
class Case91_ConsoleLogger : Case91_Logger

@Bind_Case91(to = Case91_UserRepository::class)
class Case91_InMemoryUserRepository : Case91_UserRepository

// ============================================================================
// ケース92: 設定キー (config keys) の収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ConfigKey_Case92(
    val key: String,
    val description: String,
    val source: Source = Source(),
)

@ConfigKey_Case92(key = "db.url", description = "JDBC URL")
val case92_dbUrl = "jdbc:postgresql://localhost/test"

@ConfigKey_Case92(key = "db.maxConnections", description = "Connection pool size")
val case92_maxConn = 20

@ConfigKey_Case92(key = "log.level", description = "Logging level")
val case92_logLevel = "INFO"

// ============================================================================
// ケース93: ユーザーシナリオ (BDD 風) のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Scenario_Case93(
    val feature: String,
    val scenario: String,
    val source: Source = Source(),
)

@Scenario_Case93(feature = "Login", scenario = "valid credentials")
fun case93_loginValid() {
    // Given a registered user, When they enter correct credentials, Then they are logged in
}

@Scenario_Case93(feature = "Login", scenario = "invalid password")
fun case93_loginInvalid() {
    // Given a registered user, When wrong password, Then access denied
}

// ============================================================================
// ケース94: コード補完用のテンプレート (live template 風) 収集
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Template_Case94(
    val abbrev: String,
    val source: Source = Source(),
)

@Template_Case94(abbrev = "fori")
val case94_forI = "for (i in 0 until N) {\n    \n}"

@Template_Case94(abbrev = "psvm")
val case94_psvm = "fun main() {\n    \n}"

// ============================================================================
// ケース95: enum エントリの宣言キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case95(val source: Source = Source())

@Snippets_Case95
enum class Case95_HttpStatus(val code: Int) {
    OK(200),
    NOT_FOUND(404),
    INTERNAL_SERVER_ERROR(500),
}

// ============================================================================
// ケース96: クラス + その内部メンバ関数 (両方が別 marker でキャプチャ)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureType_Case96(val source: Source = Source())

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CaptureFn_Case96(val source: Source = Source())

@CaptureType_Case96
class Case96_Service {
    @CaptureFn_Case96
    fun execute() = 42
}

// ============================================================================
// ケース97: marker annotation 自身を SourceLocation のみで使う (軽量 location 収集)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TrackOnly_Case97(
    val location: SourceLocation = SourceLocation(),
)

@TrackOnly_Case97
fun case97_a() {
}

@TrackOnly_Case97
fun case97_b() {
}

@TrackOnly_Case97
fun case97_c() {
}

// ============================================================================
// ケース99: バックティック識別子をキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case99(val source: Source = Source())

@Snippets_Case99
fun `case99 user can login successfully`() {
}

// ============================================================================
// ケース100: 複数の filler 同時 + 複数 marker + 複数ファイルの統合シナリオ
// 複数ファイル部分は case100/FileB.kt と協調
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case100(
    val tag: String = "default",
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

@Snippets_Case100(tag = "math")
fun case100_add(a: Int, b: Int) = a + b

@Snippets_Case100(tag = "math")
val case100_pi = 3.14159

class ScenarioCasesTest : StringSpec({

    "ケース78: マイグレーション宣言の収集".config(enabled = false) {
        capturedSources<Migration_Case78>() shouldBe listOf(
            Migration_Case78(
                version = 1,
                description = "create users table",
                source = Source(
                    value = "val case78_v1 = \"\"\"CREATE TABLE users (id BIGINT PRIMARY KEY, name TEXT)\"\"\"",
                ),
            ),
            Migration_Case78(
                version = 2,
                description = "add email column",
                source = Source(
                    value = "val case78_v2 = \"\"\"ALTER TABLE users ADD COLUMN email TEXT\"\"\"",
                ),
            ),
        )
    }

    "ケース79: テストフィクスチャの収集".config(enabled = false) {
        capturedSources<Fixture_Case79>() shouldBe listOf(
            Fixture_Case79(
                name = "minimal-user",
                source = Source(value = "fun case79_minimalUser() = \"{\\\"id\\\":1}\""),
            ),
            Fixture_Case79(
                name = "full-user",
                source = Source(
                    value = "fun case79_fullUser() = \"{\\\"id\\\":1,\\\"name\\\":\\\"x\\\",\\\"email\\\":\\\"y\\\"}\"",
                ),
            ),
        )
    }

    "ケース80: ベンチマーク対象の収集".config(enabled = false) {
        capturedSources<Benchmark_Case80>() shouldBe listOf(
            Benchmark_Case80(
                name = "sum-1000",
                iterations = 1000,
                source = Source(value = "fun case80_sum1000(): Int = (1..1000).sum()"),
            ),
            Benchmark_Case80(
                name = "sum-1000000",
                iterations = 100,
                source = Source(value = "fun case80_sum1m(): Int = (1..1_000_000).sum()"),
            ),
        )
    }

    "ケース81: GraphQL resolver の収集".config(enabled = false) {
        capturedSources<GraphQLResolver_Case81>() shouldBe listOf(
            GraphQLResolver_Case81(
                type = "User",
                field = "name",
                source = Source(value = "fun case81_userName(): String = \"Tsubasa\""),
            ),
            GraphQLResolver_Case81(
                type = "User",
                field = "email",
                source = Source(value = "fun case81_userEmail(): String = \"tsubasa@example.com\""),
            ),
        )
    }

    "ケース82: CLI コマンドのカタログ".config(enabled = false) {
        capturedSources<Command_Case82>() shouldBe listOf(
            Command_Case82(
                name = "build",
                help = "Build the project",
                source = Source(value = "fun case82_build() = println(\"building\")"),
            ),
            Command_Case82(
                name = "test",
                help = "Run tests",
                source = Source(value = "fun case82_test() = println(\"testing\")"),
            ),
            Command_Case82(
                name = "clean",
                help = "Remove build artifacts",
                source = Source(value = "fun case82_clean() = println(\"cleaning\")"),
            ),
        )
    }

    "ケース83: ドキュメント用のサンプル収集".config(enabled = false) {
        capturedSources<DocSample_Case83>() shouldBe listOf(
            DocSample_Case83(
                title = "Hello World",
                source = Source(value = "fun case83_helloWorld() {\n    println(\"Hello, World!\")\n}"),
            ),
            DocSample_Case83(
                title = "List filtering",
                source = Source(
                    value = "fun case83_filterEven() {\n    val evens = (1..10).filter { it % 2 == 0 }\n    println(evens)\n}",
                ),
            ),
        )
    }

    "ケース84: DSL 利用例の収集".config(enabled = false) {
        capturedSources<DslExample_Case84>() shouldBe listOf(
            DslExample_Case84(
                source = Source(
                    value = "case84_html {\n    div {\n        p(\"Hello\")\n        p(\"World\")\n    }\n}",
                ),
            ),
        )
    }

    "ケース85: schema 宣言の収集".config(enabled = false) {
        capturedSources<Table_Case85>() shouldBe listOf(
            Table_Case85(
                name = "users",
                source = Source(
                    value = "class Case85_UsersTable {\n    val id = \"id INT PRIMARY KEY\"\n    val name = \"name TEXT NOT NULL\"\n}",
                ),
            ),
            Table_Case85(
                name = "posts",
                source = Source(
                    value = "class Case85_PostsTable {\n    val id = \"id INT PRIMARY KEY\"\n    val userId = \"user_id INT NOT NULL\"\n    val body = \"body TEXT\"\n}",
                ),
            ),
        )
    }

    "ケース86: feature flag のカタログ".config(enabled = false) {
        capturedSources<Flag_Case86>() shouldBe listOf(
            Flag_Case86(
                key = "new-search",
                defaultEnabled = false,
                source = Source(value = "val case86_newSearchEnabled = false"),
            ),
            Flag_Case86(
                key = "dark-mode",
                defaultEnabled = true,
                source = Source(value = "val case86_darkModeEnabled = true"),
            ),
        )
    }

    "ケース87: コードレビュー チェックリスト項目の収集".config(enabled = false) {
        capturedSources<ChecklistItem_Case87>() shouldBe listOf(
            ChecklistItem_Case87(
                category = ChecklistItem_Case87.Category.Naming,
                source = Source(value = "fun case87_checkNamingConvention() = Unit"),
            ),
            ChecklistItem_Case87(
                category = ChecklistItem_Case87.Category.Safety,
                source = Source(value = "fun case87_checkNullability() = Unit"),
            ),
            ChecklistItem_Case87(
                category = ChecklistItem_Case87.Category.Performance,
                source = Source(value = "fun case87_checkCollectionAllocation() = Unit"),
            ),
        )
    }

    "ケース88: tutorial step (順序付き) のキャプチャ".config(enabled = false) {
        capturedSources<TutorialStep_Case88>() shouldBe listOf(
            TutorialStep_Case88(
                order = 1,
                title = "Install",
                source = Source(value = "fun case88_step1() = \"Run `brew install foo`\""),
            ),
            TutorialStep_Case88(
                order = 2,
                title = "Configure",
                source = Source(value = "fun case88_step2() = \"Edit ~/.foorc\""),
            ),
            TutorialStep_Case88(
                order = 3,
                title = "Run",
                source = Source(value = "fun case88_step3() = \"Execute `foo run`\""),
            ),
        )
    }

    "ケース89: REST controller (Spring 風) の API カタログ".config(enabled = false) {
        val captured = capturedSources<RestRoute_Case89>()
        captured.size shouldBe 2
        captured[0].method shouldBe RestRoute_Case89.HttpVerb.GET
        captured[0].path shouldBe "/users/{id}"
        captured[1].method shouldBe RestRoute_Case89.HttpVerb.POST
        captured[1].path shouldBe "/users"
    }

    "ケース90: validator rule のカタログ".config(enabled = false) {
        capturedSources<Rule_Case90>() shouldBe listOf(
            Rule_Case90(
                message = "name must not be blank",
                source = Source(
                    value = "val case90_nameNonBlank: (String) -> Boolean = { it.isNotBlank() }",
                ),
            ),
            Rule_Case90(
                message = "email must contain @",
                source = Source(
                    value = "val case90_emailHasAt: (String) -> Boolean = { it.contains('@') }",
                ),
            ),
        )
    }

    "ケース91: DI binding のカタログ".config(enabled = false) {
        capturedSources<Bind_Case91>() shouldBe listOf(
            Bind_Case91(
                to = Case91_Logger::class,
                source = Source(value = "class Case91_ConsoleLogger : Case91_Logger"),
            ),
            Bind_Case91(
                to = Case91_UserRepository::class,
                source = Source(value = "class Case91_InMemoryUserRepository : Case91_UserRepository"),
            ),
        )
    }

    "ケース92: 設定キー (config keys) の収集".config(enabled = false) {
        capturedSources<ConfigKey_Case92>() shouldBe listOf(
            ConfigKey_Case92(
                key = "db.url",
                description = "JDBC URL",
                source = Source(value = "val case92_dbUrl = \"jdbc:postgresql://localhost/test\""),
            ),
            ConfigKey_Case92(
                key = "db.maxConnections",
                description = "Connection pool size",
                source = Source(value = "val case92_maxConn = 20"),
            ),
            ConfigKey_Case92(
                key = "log.level",
                description = "Logging level",
                source = Source(value = "val case92_logLevel = \"INFO\""),
            ),
        )
    }

    "ケース93: ユーザーシナリオ (BDD 風) のキャプチャ".config(enabled = false) {
        capturedSources<Scenario_Case93>() shouldBe listOf(
            Scenario_Case93(
                feature = "Login",
                scenario = "valid credentials",
                source = Source(
                    value = "fun case93_loginValid() {\n    // Given a registered user, When they enter correct credentials, Then they are logged in\n}",
                ),
            ),
            Scenario_Case93(
                feature = "Login",
                scenario = "invalid password",
                source = Source(
                    value = "fun case93_loginInvalid() {\n    // Given a registered user, When wrong password, Then access denied\n}",
                ),
            ),
        )
    }

    "ケース94: コード補完用のテンプレート (live template 風) 収集".config(enabled = false) {
        capturedSources<Template_Case94>() shouldBe listOf(
            Template_Case94(
                abbrev = "fori",
                source = Source(value = "val case94_forI = \"for (i in 0 until N) {\\n    \\n}\""),
            ),
            Template_Case94(
                abbrev = "psvm",
                source = Source(value = "val case94_psvm = \"fun main() {\\n    \\n}\""),
            ),
        )
    }

    "ケース95: enum エントリの宣言キャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case95>() shouldBe listOf(
            Snippets_Case95(
                source = Source(
                    value = "enum class Case95_HttpStatus(val code: Int) {\n    OK(200),\n    NOT_FOUND(404),\n    INTERNAL_SERVER_ERROR(500),\n}",
                ),
            ),
        )
    }

    "ケース96: クラス + その内部メンバ関数 (両方が別 marker でキャプチャ - Type 側)".config(enabled = false) {
        capturedSources<CaptureType_Case96>() shouldBe listOf(
            CaptureType_Case96(
                source = Source(value = "class Case96_Service {\n    @CaptureFn_Case96\n    fun execute() = 42\n}"),
            ),
        )
    }

    "ケース96: クラス + その内部メンバ関数 (両方が別 marker でキャプチャ - Fn 側)".config(enabled = false) {
        capturedSources<CaptureFn_Case96>() shouldBe listOf(
            CaptureFn_Case96(source = Source(value = "fun execute() = 42")),
        )
    }

    "ケース97: marker annotation 自身を SourceLocation のみで使う (軽量 location 収集)".config(enabled = false) {
        val captured = capturedSources<TrackOnly_Case97>()
        captured.size shouldBe 3
        captured.forEach { it.location.packageName shouldBe "me.tbsten.capture.code.testapp" }
    }

    "ケース98: 順序が保たれることの検証 (interleaved な順序)".config(enabled = false) {
        // marker と sites は case98/FileA.kt / case98/FileB.kt / case98/FileA2.kt に配置
        val captured = capturedSources<Snippets_Case98>()
        captured.size shouldBe 4
        captured shouldBe listOf(
            Snippets_Case98(source = Source(value = "fun case98_a1() {}")),
            Snippets_Case98(source = Source(value = "fun case98_a2() {}")),
            Snippets_Case98(source = Source(value = "fun case98_a3() {}")),
            Snippets_Case98(source = Source(value = "fun case98_b1() {}")),
        )
    }

    "ケース99: バックティック識別子をキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case99>() shouldBe listOf(
            Snippets_Case99(source = Source(value = "fun `case99 user can login successfully`() {}")),
        )
    }

    "ケース100: 複数の filler 同時 + 複数 marker + 複数ファイルの統合シナリオ".config(enabled = false) {
        // fileA: case100_add, case100_pi (こちらのファイル) — tag="math"
        // fileB: case100_greet — tag="default" (case100/FileB.kt)
        val captured = capturedSources<Snippets_Case100>()
        captured.size shouldBe 3
    }
})
