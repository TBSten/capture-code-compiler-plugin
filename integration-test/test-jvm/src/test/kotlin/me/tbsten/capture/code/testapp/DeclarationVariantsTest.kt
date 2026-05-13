package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// ケース33: クラス内のメンバ関数をキャプチャ (インデントを dedent)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MemberSnippet_Case33(val source: Source = Source())

class Case33_Repository {
    @MemberSnippet_Case33
    fun findById(id: Int): String? {
        return null
    }
}

// ============================================================================
// ケース34: 多重ネストクラス内の関数キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DeepSnippet_Case34(val source: Source = Source())

class Case34_Outer {
    class Inner {
        class Deepest {
            @DeepSnippet_Case34
            fun deepFunc() = "deep"
        }
    }
}

// ============================================================================
// ケース35: data class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case35(val source: Source = Source())

@Snippets_Case35
data class Case35_User(val id: Long, val name: String, val email: String)

// ============================================================================
// ケース36: sealed class とその子クラスのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case36(val source: Source = Source())

@Snippets_Case36
sealed class Case36_Result {
    data class Success(val value: String) : Case36_Result()
    data class Failure(val error: Throwable) : Case36_Result()
}

// ============================================================================
// ケース37: enum class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case37(val source: Source = Source())

@Snippets_Case37
enum class Case37_Direction { NORTH, SOUTH, EAST, WEST }

// ============================================================================
// ケース38: inline value class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case38(val source: Source = Source())

@Snippets_Case38
@JvmInline
value class Case38_UserId(val raw: Long)

// ============================================================================
// ケース39: abstract class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case39(val source: Source = Source())

@Snippets_Case39
abstract class Case39_Shape {
    abstract fun area(): Double
}

// ============================================================================
// ケース40: interface のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case40(val source: Source = Source())

@Snippets_Case40
interface Case40_Repository<T> {
    fun findById(id: Long): T?
    fun save(entity: T)
}

// ============================================================================
// ケース41: companion object のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case41(val source: Source = Source())

class Case41_Foo {
    @Snippets_Case41
    companion object {
        const val NAME = "Foo"
    }
}

// ============================================================================
// ケース42: ジェネリックなクラスのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case42(val source: Source = Source())

@Snippets_Case42
class Case42_Box<T : Any>(val value: T)

// ============================================================================
// ケース43: ジェネリックな関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case43(val source: Source = Source())

@Snippets_Case43
fun <T> case43_identity(x: T): T = x

// ============================================================================
// ケース44: suspend function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case44(val source: Source = Source())

@Snippets_Case44
suspend fun case44_fetchUser(id: Long): String {
    return "user-$id"
}

// ============================================================================
// ケース45: inline function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case45(val source: Source = Source())

@Snippets_Case45
inline fun <reified T> case45_typeName(): String = T::class.simpleName ?: "?"

// ============================================================================
// ケース46: extension function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case46(val source: Source = Source())

@Snippets_Case46
fun String.case46_shout(): String = uppercase() + "!"

// ============================================================================
// ケース47: operator function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case47(val source: Source = Source())

data class Case47_Vec(val x: Int, val y: Int) {
    @Snippets_Case47
    operator fun plus(other: Case47_Vec): Case47_Vec = Case47_Vec(x + other.x, y + other.y)
}

// ============================================================================
// ケース48: infix function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case48(val source: Source = Source())

class Case48_Money(val amount: Int) {
    @Snippets_Case48
    infix fun plus(other: Case48_Money): Case48_Money = Case48_Money(amount + other.amount)
}

// ============================================================================
// ケース49: デフォルト引数を持つ関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case49(val source: Source = Source())

@Snippets_Case49
fun case49_connect(host: String = "localhost", port: Int = 8080, useSsl: Boolean = false) {
    println("Connecting to $host:$port (ssl=$useSsl)")
}

// ============================================================================
// ケース50: vararg を持つ関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case50(val source: Source = Source())

@Snippets_Case50
fun case50_joinAll(separator: String, vararg parts: String): String = parts.joinToString(separator)

// ============================================================================
// ケース51: lateinit var のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case51(val source: Source = Source())

class Case51_App {
    @Snippets_Case51
    lateinit var name: String
}

// ============================================================================
// ケース52: const val のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case52(val source: Source = Source())

object Case52_Constants {
    @Snippets_Case52
    const val MAX_RETRY = 3
}

// ============================================================================
// ケース53: by lazy デリゲートのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case53(val source: Source = Source())

private fun case53_computeHeavy(): String = "computed"

@Snippets_Case53
val case53_heavy: String by lazy { case53_computeHeavy() }

// ============================================================================
// ケース54: カスタムゲッターを持つ property
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case54(val source: Source = Source())

class Case54_Person(val first: String, val last: String) {
    @Snippets_Case54
    val fullName: String
        get() = "$first $last"
}

// ============================================================================
// ケース55: trailing-comma 多パラメータ data class
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippets_Case55(val source: Source = Source())

@Snippets_Case55
data class Case55_Config(
    val host: String,
    val port: Int,
    val timeout: Int,
    val retries: Int,
)

class DeclarationVariantsTest : StringSpec({

    "ケース33: クラス内のメンバ関数をキャプチャ (インデントを dedent)".config(enabled = false) {
        capturedSources<MemberSnippet_Case33>() shouldBe listOf(
            MemberSnippet_Case33(
                source = Source(value = "fun findById(id: Int): String? {\n    return null\n}"),
            ),
        )
    }

    "ケース34: 多重ネストクラス内の関数キャプチャ".config(enabled = false) {
        capturedSources<DeepSnippet_Case34>() shouldBe listOf(
            DeepSnippet_Case34(source = Source(value = "fun deepFunc() = \"deep\"")),
        )
    }

    "ケース35: data class のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case35>() shouldBe listOf(
            Snippets_Case35(
                source = Source(
                    value = "data class Case35_User(val id: Long, val name: String, val email: String)",
                ),
            ),
        )
    }

    "ケース36: sealed class とその子クラスのキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case36>() shouldBe listOf(
            Snippets_Case36(
                source = Source(
                    value = "sealed class Case36_Result {\n    data class Success(val value: String) : Case36_Result()\n    data class Failure(val error: Throwable) : Case36_Result()\n}",
                ),
            ),
        )
    }

    "ケース37: enum class のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case37>() shouldBe listOf(
            Snippets_Case37(
                source = Source(value = "enum class Case37_Direction { NORTH, SOUTH, EAST, WEST }"),
            ),
        )
    }

    "ケース38: inline value class のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case38>() shouldBe listOf(
            Snippets_Case38(
                source = Source(value = "@JvmInline\nvalue class Case38_UserId(val raw: Long)"),
            ),
        )
    }

    "ケース39: abstract class のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case39>() shouldBe listOf(
            Snippets_Case39(
                source = Source(
                    value = "abstract class Case39_Shape {\n    abstract fun area(): Double\n}",
                ),
            ),
        )
    }

    "ケース40: interface のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case40>() shouldBe listOf(
            Snippets_Case40(
                source = Source(
                    value = "interface Case40_Repository<T> {\n    fun findById(id: Long): T?\n    fun save(entity: T)\n}",
                ),
            ),
        )
    }

    "ケース41: companion object のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case41>() shouldBe listOf(
            Snippets_Case41(
                source = Source(value = "companion object {\n    const val NAME = \"Foo\"\n}"),
            ),
        )
    }

    "ケース42: ジェネリックなクラスのキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case42>() shouldBe listOf(
            Snippets_Case42(source = Source(value = "class Case42_Box<T : Any>(val value: T)")),
        )
    }

    "ケース43: ジェネリックな関数のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case43>() shouldBe listOf(
            Snippets_Case43(source = Source(value = "fun <T> case43_identity(x: T): T = x")),
        )
    }

    "ケース44: suspend function のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case44>() shouldBe listOf(
            Snippets_Case44(
                source = Source(
                    value = "suspend fun case44_fetchUser(id: Long): String {\n    return \"user-\$id\"\n}",
                ),
            ),
        )
    }

    "ケース45: inline function のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case45>() shouldBe listOf(
            Snippets_Case45(
                source = Source(
                    value = "inline fun <reified T> case45_typeName(): String = T::class.simpleName ?: \"?\"",
                ),
            ),
        )
    }

    "ケース46: extension function のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case46>() shouldBe listOf(
            Snippets_Case46(
                source = Source(value = "fun String.case46_shout(): String = uppercase() + \"!\""),
            ),
        )
    }

    "ケース47: operator function のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case47>() shouldBe listOf(
            Snippets_Case47(
                source = Source(
                    value = "operator fun plus(other: Case47_Vec): Case47_Vec = Case47_Vec(x + other.x, y + other.y)",
                ),
            ),
        )
    }

    "ケース48: infix function のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case48>() shouldBe listOf(
            Snippets_Case48(
                source = Source(
                    value = "infix fun plus(other: Case48_Money): Case48_Money = Case48_Money(amount + other.amount)",
                ),
            ),
        )
    }

    "ケース49: デフォルト引数を持つ関数のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case49>() shouldBe listOf(
            Snippets_Case49(
                source = Source(
                    value = "fun case49_connect(host: String = \"localhost\", port: Int = 8080, useSsl: Boolean = false) {\n    println(\"Connecting to \$host:\$port (ssl=\$useSsl)\")\n}",
                ),
            ),
        )
    }

    "ケース50: vararg を持つ関数のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case50>() shouldBe listOf(
            Snippets_Case50(
                source = Source(
                    value = "fun case50_joinAll(separator: String, vararg parts: String): String = parts.joinToString(separator)",
                ),
            ),
        )
    }

    "ケース51: lateinit var のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case51>() shouldBe listOf(
            Snippets_Case51(source = Source(value = "lateinit var name: String")),
        )
    }

    "ケース52: const val のキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case52>() shouldBe listOf(
            Snippets_Case52(source = Source(value = "const val MAX_RETRY = 3")),
        )
    }

    "ケース53: by lazy デリゲートのキャプチャ".config(enabled = false) {
        capturedSources<Snippets_Case53>() shouldBe listOf(
            Snippets_Case53(source = Source(value = "val case53_heavy: String by lazy { case53_computeHeavy() }")),
        )
    }

    "ケース54: カスタムゲッターを持つ property".config(enabled = false) {
        capturedSources<Snippets_Case54>() shouldBe listOf(
            Snippets_Case54(
                source = Source(value = "val fullName: String\n    get() = \"\$first \$last\""),
            ),
        )
    }

    "ケース55: trailing-comma 多パラメータ data class".config(enabled = false) {
        capturedSources<Snippets_Case55>() shouldBe listOf(
            Snippets_Case55(
                source = Source(
                    value = "data class Case55_Config(\n    val host: String,\n    val port: Int,\n    val timeout: Int,\n    val retries: Int,\n)",
                ),
            ),
        )
    }
})
