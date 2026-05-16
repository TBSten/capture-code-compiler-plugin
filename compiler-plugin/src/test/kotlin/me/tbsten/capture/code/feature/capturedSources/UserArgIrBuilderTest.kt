package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * ユーザ定義パラメータの IR 化を end-to-end で検証する。
 *
 * `UserArgIrBuilder` 自体は `:compat-k2000` の内部実装 (internal object) のため、
 * 直接 unit test するのが難しい。代わりに本テストでは kctfork で marker class に
 * ユーザ定義パラメータを宣言した場合、`capturedSources<T>()` の書き換え結果に
 * その値が正しく反映されるかを確認する。
 *
 * Kotlin annotation 制約の 6 種類 + Default 値の合計 7 ケースをカバーする:
 *
 * 1. Int (primitive) パラメータ
 * 2. Boolean (primitive) パラメータ
 * 3. String パラメータ
 * 4. KClass パラメータ
 * 5. Enum パラメータ
 * 6. 配列 (Array<String>) パラメータ
 * 7. Nested annotation パラメータ
 * 8. Default 値が使われるパラメータ (call site で省略)
 *
 * filler ([FillerBuilderTest]) と ユーザ定義 (本テスト) を分けて検証することで、
 * `K200CapturedSourcesRewriter.buildMarkerInstance` の分岐両側を担保する。
 */
class UserArgIrBuilderTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /** capture サイトを返す `Main.captured()` を呼び出して `List<*>` を取得するヘルパ。 */
    fun loadCaptured(result: JvmCompilationResult, mainFqn: String = "example.Main"): List<*> {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    /** annotation インスタンスから引数を reflection で取得する。 */
    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    // ----------------------------------------------------------------
    // 1. primitive Int
    // ----------------------------------------------------------------
    test("Int parameter: user-specified value is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "IntParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Prio(val priority: Int)

                @Prio(priority = 42)
                internal fun greet() = "hi"

                internal object Main {
                    fun captured(): List<Prio> = capturedSources<Prio>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        annotationProperty(captured[0] as Annotation, "priority") shouldBe 42
    }

    // ----------------------------------------------------------------
    // 2. primitive Boolean
    // ----------------------------------------------------------------
    test("Boolean parameter: user-specified value is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "BoolParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Feat(val enabled: Boolean)

                @Feat(enabled = true)
                internal fun a() = 1

                @Feat(enabled = false)
                internal fun b() = 2

                internal object Main {
                    fun captured(): List<Feat> = capturedSources<Feat>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        annotationProperty(captured[0] as Annotation, "enabled") shouldBe true
        annotationProperty(captured[1] as Annotation, "enabled") shouldBe false
    }

    // ----------------------------------------------------------------
    // 3. String
    // ----------------------------------------------------------------
    test("String parameter: user-specified value is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "StringParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Labeled(val label: String)

                @Labeled(label = "primary")
                internal fun a() = 1

                internal object Main {
                    fun captured(): List<Labeled> = capturedSources<Labeled>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        annotationProperty(captured[0] as Annotation, "label") shouldBe "primary"
    }

    // ----------------------------------------------------------------
    // 4. KClass
    // ----------------------------------------------------------------
    test("KClass parameter: user-specified reference is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "KClassParam.kt",
                """
                package example

                import kotlin.reflect.KClass
                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                internal interface Service

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BoundTo(val target: KClass<*>)

                @BoundTo(target = Service::class)
                internal class ServiceImpl : Service

                internal object Main {
                    fun captured(): List<BoundTo> = capturedSources<BoundTo>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        // Note: Kotlin annotation の KClass パラメータを java.lang.reflect 経由で取得すると
        // `java.lang.Class` が返ってくる (Kotlin reflection の Class.kotlin 経由でしか KClass にできない)。
        // ここでは Class<*> の name を assert する。
        val target = annotationProperty(captured[0] as Annotation, "target") as Class<*>
        target.name shouldBe "example.Service"
    }

    // ----------------------------------------------------------------
    // 5. Enum
    // ----------------------------------------------------------------
    test("Enum parameter: user-specified entry is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "EnumParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Tag(val verb: Verb) {
                    enum class Verb { GET, POST, PUT }
                }

                @Tag(verb = Tag.Verb.GET)
                internal fun list() = "[]"

                @Tag(verb = Tag.Verb.POST)
                internal fun create() = "ok"

                internal object Main {
                    fun captured(): List<Tag> = capturedSources<Tag>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 2
        (annotationProperty(captured[0] as Annotation, "verb") as Enum<*>).name shouldBe "GET"
        (annotationProperty(captured[1] as Annotation, "verb") as Enum<*>).name shouldBe "POST"
    }

    // ----------------------------------------------------------------
    // 6. Array<String>
    // ----------------------------------------------------------------
    test("Array parameter: user-specified elements are preserved") {
        val result = compile(
            SourceFile.kotlin(
                "ArrayParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Tags(val tags: Array<String>)

                @Tags(tags = ["fast", "unit"])
                internal fun a() = 1

                internal object Main {
                    fun captured(): List<Tags> = capturedSources<Tags>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val tags = annotationProperty(captured[0] as Annotation, "tags") as Array<*>
        tags.toList() shouldBe listOf("fast", "unit")
    }

    // ----------------------------------------------------------------
    // 7. Nested annotation
    // ----------------------------------------------------------------
    test("Nested annotation parameter: user-specified instance is preserved") {
        val result = compile(
            SourceFile.kotlin(
                "NestedAnno.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                internal annotation class Author(val name: String)

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Doc(val author: Author)

                @Doc(author = Author(name = "Tsubasa"))
                internal fun greet() = "hi"

                internal object Main {
                    fun captured(): List<Doc> = capturedSources<Doc>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 1
        val author = annotationProperty(captured[0] as Annotation, "author") as Annotation
        annotationProperty(author, "name") shouldBe "Tsubasa"
    }

    // ----------------------------------------------------------------
    // 8. Default 値が使われる
    // ----------------------------------------------------------------
    test("Default value: omitted argument falls back to marker primary constructor default") {
        val result = compile(
            SourceFile.kotlin(
                "DefaultParam.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class WithDefaults(
                    val label: String = "untitled",
                    val priority: Int = 0,
                )

                @WithDefaults
                internal fun a() = 1

                @WithDefaults(label = "custom")
                internal fun b() = 2

                @WithDefaults(priority = 9)
                internal fun c() = 3

                internal object Main {
                    fun captured(): List<WithDefaults> = capturedSources<WithDefaults>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCaptured(result)
        captured.size shouldBe 3
        // a: 両方デフォルト
        annotationProperty(captured[0] as Annotation, "label") shouldBe "untitled"
        annotationProperty(captured[0] as Annotation, "priority") shouldBe 0
        // b: label だけ user 指定
        annotationProperty(captured[1] as Annotation, "label") shouldBe "custom"
        annotationProperty(captured[1] as Annotation, "priority") shouldBe 0
        // c: priority だけ user 指定
        annotationProperty(captured[2] as Annotation, "label") shouldBe "untitled"
        annotationProperty(captured[2] as Annotation, "priority") shouldBe 9
    }
})
