package me.tbsten.capture.code.testapp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.capturedSources

// ============================================================================
// クラス内のメンバ関数をキャプチャ (インデントを dedent)
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class MemberFunctionSnippet(val source: Source = Source())

class MemberFunctionRepository {
    @MemberFunctionSnippet
    fun findById(id: Int): String? {
        return null
    }
}

// ============================================================================
// 多重ネストクラス内の関数キャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NestedClassMethodSnippet(val source: Source = Source())

class NestedClassOuter {
    class Inner {
        class Deepest {
            @NestedClassMethodSnippet
            fun deepFunc() = "deep"
        }
    }
}

// ============================================================================
// data class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DataClassSnippet(val source: Source = Source())

@DataClassSnippet
data class DataClassUser(val id: Long, val name: String, val email: String)

// ============================================================================
// sealed class とその子クラスのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SealedClassSnippet(val source: Source = Source())

@SealedClassSnippet
sealed class SealedClassResult {
    data class Success(val value: String) : SealedClassResult()
    data class Failure(val error: Throwable) : SealedClassResult()
}

// ============================================================================
// enum class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class EnumClassSnippet(val source: Source = Source())

@EnumClassSnippet
enum class EnumClassDirection { NORTH, SOUTH, EAST, WEST }

// ============================================================================
// inline value class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class InlineValueClassSnippet(val source: Source = Source())

@InlineValueClassSnippet
@JvmInline
value class InlineValueUserId(val raw: Long)

// ============================================================================
// abstract class のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class AbstractClassSnippet(val source: Source = Source())

@AbstractClassSnippet
abstract class AbstractShape {
    abstract fun area(): Double
}

// ============================================================================
// interface のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class InterfaceSnippet(val source: Source = Source())

@InterfaceSnippet
interface InterfaceRepository<T> {
    fun findById(id: Long): T?
    fun save(entity: T)
}

// ============================================================================
// companion object のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CompanionObjectSnippet(val source: Source = Source())

class CompanionObjectFoo {
    @CompanionObjectSnippet
    companion object {
        const val NAME = "Foo"
    }
}

// ============================================================================
// ジェネリックなクラスのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GenericClassSnippet(val source: Source = Source())

@GenericClassSnippet
class GenericBox<T : Any>(val value: T)

// ============================================================================
// ジェネリックな関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class GenericFunctionSnippet(val source: Source = Source())

@GenericFunctionSnippet
fun <T> genericIdentity(x: T): T = x

// ============================================================================
// suspend function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class SuspendFunctionSnippet(val source: Source = Source())

@SuspendFunctionSnippet
suspend fun suspendFetchUser(id: Long): String {
    return "user-$id"
}

// ============================================================================
// inline function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class InlineFunctionSnippet(val source: Source = Source())

@InlineFunctionSnippet
inline fun <reified T> inlineTypeName(): String = T::class.simpleName ?: "?"

// ============================================================================
// extension function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ExtensionFunctionSnippet(val source: Source = Source())

@ExtensionFunctionSnippet
fun String.extensionShout(): String = uppercase() + "!"

// ============================================================================
// operator function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class OperatorFunctionSnippet(val source: Source = Source())

data class OperatorVec(val x: Int, val y: Int) {
    @OperatorFunctionSnippet
    operator fun plus(other: OperatorVec): OperatorVec = OperatorVec(x + other.x, y + other.y)
}

// ============================================================================
// infix function のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class InfixFunctionSnippet(val source: Source = Source())

class InfixMoney(val amount: Int) {
    @InfixFunctionSnippet
    infix fun plus(other: InfixMoney): InfixMoney = InfixMoney(amount + other.amount)
}

// ============================================================================
// デフォルト引数を持つ関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DefaultArgumentSnippet(val source: Source = Source())

@DefaultArgumentSnippet
fun defaultArgConnect(host: String = "localhost", port: Int = 8080, useSsl: Boolean = false) {
    println("Connecting to $host:$port (ssl=$useSsl)")
}

// ============================================================================
// vararg を持つ関数のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class VarargFunctionSnippet(val source: Source = Source())

@VarargFunctionSnippet
fun varargJoinAll(separator: String, vararg parts: String): String = parts.joinToString(separator)

// ============================================================================
// lateinit var のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LateinitPropertySnippet(val source: Source = Source())

class LateinitApp {
    @LateinitPropertySnippet
    lateinit var name: String
}

// ============================================================================
// const val のキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class ConstValSnippet(val source: Source = Source())

object ConstValConstants {
    @ConstValSnippet
    const val MAX_RETRY = 3
}

// ============================================================================
// by lazy デリゲートのキャプチャ
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class LazyDelegateSnippet(val source: Source = Source())

private fun lazyComputeHeavy(): String = "computed"

@LazyDelegateSnippet
val lazyHeavy: String by lazy { lazyComputeHeavy() }

// ============================================================================
// カスタムゲッターを持つ property
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
internal annotation class CustomGetterSnippet(val source: Source = Source())

class CustomGetterPerson(val first: String, val last: String) {
    @CustomGetterSnippet
    val fullName: String
        get() = "$first $last"
}

// ============================================================================
// trailing-comma 多パラメータ data class
// ============================================================================
@CaptureCode
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TrailingCommaDataClassSnippet(val source: Source = Source())

@TrailingCommaDataClassSnippet
data class TrailingCommaConfig(
    val host: String,
    val port: Int,
    val timeout: Int,
    val retries: Int,
)

class DeclarationVariantsTest : StringSpec({

    "クラス内のメンバ関数をキャプチャ (インデントを dedent)" {
        capturedSources<MemberFunctionSnippet>() shouldBe listOf(
            MemberFunctionSnippet(
                source = Source(value = "fun findById(id: Int): String? {\n    return null\n}"),
            ),
        )
    }

    "多重ネストクラス内の関数キャプチャ" {
        capturedSources<NestedClassMethodSnippet>() shouldBe listOf(
            NestedClassMethodSnippet(source = Source(value = "fun deepFunc() = \"deep\"")),
        )
    }

    "data class のキャプチャ" {
        capturedSources<DataClassSnippet>() shouldBe listOf(
            DataClassSnippet(
                source = Source(
                    value = "data class DataClassUser(val id: Long, val name: String, val email: String)",
                ),
            ),
        )
    }

    "sealed class とその子クラスのキャプチャ" {
        capturedSources<SealedClassSnippet>() shouldBe listOf(
            SealedClassSnippet(
                source = Source(
                    value = "sealed class SealedClassResult {\n    data class Success(val value: String) : SealedClassResult()\n    data class Failure(val error: Throwable) : SealedClassResult()\n}",
                ),
            ),
        )
    }

    "enum class のキャプチャ" {
        capturedSources<EnumClassSnippet>() shouldBe listOf(
            EnumClassSnippet(
                source = Source(value = "enum class EnumClassDirection { NORTH, SOUTH, EAST, WEST }"),
            ),
        )
    }

    "inline value class のキャプチャ" {
        capturedSources<InlineValueClassSnippet>() shouldBe listOf(
            InlineValueClassSnippet(
                source = Source(value = "@JvmInline\nvalue class InlineValueUserId(val raw: Long)"),
            ),
        )
    }

    "abstract class のキャプチャ" {
        capturedSources<AbstractClassSnippet>() shouldBe listOf(
            AbstractClassSnippet(
                source = Source(
                    value = "abstract class AbstractShape {\n    abstract fun area(): Double\n}",
                ),
            ),
        )
    }

    "interface のキャプチャ" {
        capturedSources<InterfaceSnippet>() shouldBe listOf(
            InterfaceSnippet(
                source = Source(
                    value = "interface InterfaceRepository<T> {\n    fun findById(id: Long): T?\n    fun save(entity: T)\n}",
                ),
            ),
        )
    }

    "companion object のキャプチャ" {
        capturedSources<CompanionObjectSnippet>() shouldBe listOf(
            CompanionObjectSnippet(
                source = Source(value = "companion object {\n    const val NAME = \"Foo\"\n}"),
            ),
        )
    }

    "ジェネリックなクラスのキャプチャ" {
        capturedSources<GenericClassSnippet>() shouldBe listOf(
            GenericClassSnippet(source = Source(value = "class GenericBox<T : Any>(val value: T)")),
        )
    }

    "ジェネリックな関数のキャプチャ" {
        capturedSources<GenericFunctionSnippet>() shouldBe listOf(
            GenericFunctionSnippet(source = Source(value = "fun <T> genericIdentity(x: T): T = x")),
        )
    }

    "suspend function のキャプチャ" {
        capturedSources<SuspendFunctionSnippet>() shouldBe listOf(
            SuspendFunctionSnippet(
                source = Source(
                    value = "suspend fun suspendFetchUser(id: Long): String {\n    return \"user-\$id\"\n}",
                ),
            ),
        )
    }

    "inline function のキャプチャ" {
        capturedSources<InlineFunctionSnippet>() shouldBe listOf(
            InlineFunctionSnippet(
                source = Source(
                    value = "inline fun <reified T> inlineTypeName(): String = T::class.simpleName ?: \"?\"",
                ),
            ),
        )
    }

    "extension function のキャプチャ" {
        capturedSources<ExtensionFunctionSnippet>() shouldBe listOf(
            ExtensionFunctionSnippet(
                source = Source(value = "fun String.extensionShout(): String = uppercase() + \"!\""),
            ),
        )
    }

    "operator function のキャプチャ" {
        capturedSources<OperatorFunctionSnippet>() shouldBe listOf(
            OperatorFunctionSnippet(
                source = Source(
                    value = "operator fun plus(other: OperatorVec): OperatorVec = OperatorVec(x + other.x, y + other.y)",
                ),
            ),
        )
    }

    "infix function のキャプチャ" {
        capturedSources<InfixFunctionSnippet>() shouldBe listOf(
            InfixFunctionSnippet(
                source = Source(
                    value = "infix fun plus(other: InfixMoney): InfixMoney = InfixMoney(amount + other.amount)",
                ),
            ),
        )
    }

    "デフォルト引数を持つ関数のキャプチャ" {
        capturedSources<DefaultArgumentSnippet>() shouldBe listOf(
            DefaultArgumentSnippet(
                source = Source(
                    value = "fun defaultArgConnect(host: String = \"localhost\", port: Int = 8080, useSsl: Boolean = false) {\n    println(\"Connecting to \$host:\$port (ssl=\$useSsl)\")\n}",
                ),
            ),
        )
    }

    "vararg を持つ関数のキャプチャ" {
        capturedSources<VarargFunctionSnippet>() shouldBe listOf(
            VarargFunctionSnippet(
                source = Source(
                    value = "fun varargJoinAll(separator: String, vararg parts: String): String = parts.joinToString(separator)",
                ),
            ),
        )
    }

    "lateinit var のキャプチャ" {
        capturedSources<LateinitPropertySnippet>() shouldBe listOf(
            LateinitPropertySnippet(source = Source(value = "lateinit var name: String")),
        )
    }

    "const val のキャプチャ" {
        capturedSources<ConstValSnippet>() shouldBe listOf(
            ConstValSnippet(source = Source(value = "const val MAX_RETRY = 3")),
        )
    }

    "by lazy デリゲートのキャプチャ" {
        capturedSources<LazyDelegateSnippet>() shouldBe listOf(
            LazyDelegateSnippet(source = Source(value = "val lazyHeavy: String by lazy { lazyComputeHeavy() }")),
        )
    }

    "カスタムゲッターを持つ property" {
        capturedSources<CustomGetterSnippet>() shouldBe listOf(
            CustomGetterSnippet(
                source = Source(value = "val fullName: String\n    get() = \"\$first \$last\""),
            ),
        )
    }

    "trailing-comma 多パラメータ data class" {
        capturedSources<TrailingCommaDataClassSnippet>() shouldBe listOf(
            TrailingCommaDataClassSnippet(
                source = Source(
                    value = "data class TrailingCommaConfig(\n    val host: String,\n    val port: Int,\n    val timeout: Int,\n    val retries: Int,\n)",
                ),
            ),
        )
    }
})
