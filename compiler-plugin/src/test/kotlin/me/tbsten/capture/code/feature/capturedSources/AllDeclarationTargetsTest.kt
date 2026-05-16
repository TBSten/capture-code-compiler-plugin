package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * 宣言ターゲット 5 種 (property / class / object / function / typealias) と、
 * filler 未指定 marker (ケース #8 相当) のキャプチャを kctfork で end-to-end 検証する。
 *
 * 各テストは:
 * 1. plugin enabled で marker (`@CaptureCode` 付き) と use site と `capturedSources<T>()` を
 *    1 file にまとめてコンパイル
 * 2. 生成された class file を reflection で読み、`capturedSources<T>()` が `listOf(Marker(...))` に
 *    書き換わって 1 件分の annotation instance を返すこと、その `source.value` (filler `Source` がある
 *    marker のみ) が想定通りであることを assert
 *
 * Logic B-ir (collector) / Logic C (source 取得) / Logic H (rewriter) を declaration kind ごとに通す
 * smoke test の集合。`SourceLocation` / `CaptureKind` filler / ユーザ定義パラメータ / dedent
 * (Logic D) は対象外 (それぞれ専用テストで検証する)。
 */
class AllDeclarationTargetsTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /**
     * `capturedSources<T>()` の戻り値 (`List<Annotation>`) から `source.value` を取り出すヘルパ。
     *
     * marker は SOURCE retention でも class file に annotation class として残るため、
     * reflection で `source()` メソッド (= `Source` インスタンス) を取り、その `value()` で
     * 文字列を取り出せる。
     */
    fun captureSourceValue(annotation: Annotation): String {
        val sourceMethod = annotation.annotationClass.java.getMethod("source")
        val sourceAnnotation = sourceMethod.invoke(annotation) as Annotation
        val valueMethod = sourceAnnotation.annotationClass.java.getMethod("value")
        return valueMethod.invoke(sourceAnnotation) as String
    }

    /** capture サイトを返す `Main.captured()` を呼び出して `List<*>` を取得するヘルパ。 */
    fun loadCaptured(result: JvmCompilationResult): List<*> {
        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as List<*>
    }

    // ----------------------------------------------------------------
    // 1. class declaration (CLASS kind)
    // ----------------------------------------------------------------
    test("class declaration is captured") {
        val result = compile(
            SourceFile.kotlin(
                "ClassMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ClassSnippets(val source: Source = Source())

                @ClassSnippets
                internal class TargetClass(val id: Int)

                internal object Main {
                    fun captured(): List<ClassSnippets> = capturedSources<ClassSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "internal class TargetClass(val id: Int)"
    }

    // ----------------------------------------------------------------
    // 2. object declaration (OBJECT kind)
    // ----------------------------------------------------------------
    test("object declaration is captured") {
        val result = compile(
            SourceFile.kotlin(
                "ObjectMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ObjectSnippets(val source: Source = Source())

                @ObjectSnippets
                internal object Singleton

                internal object Main {
                    fun captured(): List<ObjectSnippets> = capturedSources<ObjectSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "internal object Singleton"
    }

    // ----------------------------------------------------------------
    // 3. function declaration (FUNCTION kind)
    // ----------------------------------------------------------------
    test("function declaration is captured") {
        val result = compile(
            SourceFile.kotlin(
                "FunctionMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FunSnippets(val source: Source = Source())

                @FunSnippets
                internal fun greet(): String = "hello"

                internal object Main {
                    fun captured(): List<FunSnippets> = capturedSources<FunSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "internal fun greet(): String = \"hello\""
    }

    // ----------------------------------------------------------------
    // 4. typealias declaration (TYPEALIAS kind)
    // ----------------------------------------------------------------
    test("typealias declaration is captured") {
        val result = compile(
            SourceFile.kotlin(
                "TypeAliasMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.TYPEALIAS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class AliasSnippets(val source: Source = Source())

                @AliasSnippets
                internal typealias UserId = Long

                internal object Main {
                    fun captured(): List<AliasSnippets> = capturedSources<AliasSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe "internal typealias UserId = Long"
    }

    // ----------------------------------------------------------------
    // 5. inline value class (`@JvmInline value class`) のキャプチャ
    //
    // `skipLeadingAnnotationLines` は marker annotation のみを skip し、
    // `@JvmInline` のような **semantic-significant な Kotlin 標準 annotation** は source に残す。
    // inline value class は `@JvmInline` 無しでは valid な定義にならないため、
    // source として `@JvmInline\nvalue class Foo(...)` の形を保つ必要がある。
    // ----------------------------------------------------------------
    test("inline value class declaration is captured with @JvmInline preserved") {
        val result = compile(
            SourceFile.kotlin(
                "ValueClassMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class ValueClassSnippets(val source: Source = Source())

                @ValueClassSnippets
                @JvmInline
                internal value class UserId(val raw: Long)

                internal object Main {
                    fun captured(): List<ValueClassSnippets> = capturedSources<ValueClassSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "@JvmInline\ninternal value class UserId(val raw: Long)"
    }

    // ----------------------------------------------------------------
    // 6. annotation の重ね合わせ: marker → `@Suppress` の順で並んでも `@Suppress` を保持
    //
    // 線形 skip 実装の都合上、 先頭の marker 行群を skip した後に出現する非 marker annotation
    // (`@Suppress("unused")` 等) はそのまま source に残る。 spec 上「marker 行のみ drop し、
    // それ以外の annotation は意味を持つので残す」が本プラグインの方針。
    // ----------------------------------------------------------------
    test("non-marker annotations following the marker are preserved") {
        val result = compile(
            SourceFile.kotlin(
                "MarkerThenSuppress.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FunSnippetsKeepAnnot(val source: Source = Source())

                @FunSnippetsKeepAnnot
                @Suppress("unused")
                internal fun keep(): Int = 1

                internal object Main {
                    fun captured(): List<FunSnippetsKeepAnnot> = capturedSources<FunSnippetsKeepAnnot>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 1
        captureSourceValue(captured[0] as Annotation) shouldBe
            "@Suppress(\"unused\")\ninternal fun keep(): Int = 1"
    }

    // ----------------------------------------------------------------
    // 7. 同一行に複数 property (`@Marker val p1 = 1; @Marker val p2 = 2`) のキャプチャ
    //
    // skipLeadingAnnotationLines は token ベースで、 marker annotation 本体 (+ optional `(...)`) と
    // 直後の whitespace のみを吸収する。 これにより同一行 multi-property 形式でも各 property を
    // 独立に capture できる。 ナイーブな「次の改行まで skip」実装では 1 番目の declaration の
    // endOffset を超えて読みすぎ、 substring 取得が null になり capture できないという罠がある。
    // ----------------------------------------------------------------
    test("multiple properties on a single line are each captured") {
        val result = compile(
            SourceFile.kotlin(
                "MultiPropOneLine.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class MultiPropSnippets(val source: Source = Source())

                @MultiPropSnippets val mlP1 = 1; @MultiPropSnippets val mlP2 = 2

                internal object Main {
                    fun captured(): List<MultiPropSnippets> = capturedSources<MultiPropSnippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        captured.size shouldBe 2
        // 1 番目の property は IR 上の endOffset が `;` を含むため source に `;` が残る。
        // 同一行 multi-property は珍しい形式なので、 trailing `;` の整形は将来の polish item とする。
        captureSourceValue(captured[0] as Annotation) shouldBe "val mlP1 = 1;"
        captureSourceValue(captured[1] as Annotation) shouldBe "val mlP2 = 2"
    }

    // ----------------------------------------------------------------
    // 8. filler-less marker (ケース #8 相当) — 0-arg Marker() の list literal に書き換わる
    // ----------------------------------------------------------------
    test("filler-less marker yields zero-arg constructor calls") {
        val result = compile(
            SourceFile.kotlin(
                "FillerLessMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Bench

                @Bench
                internal fun targetA() {}

                @Bench
                internal fun targetB() {}

                internal object Main {
                    fun captured(): List<Bench> = capturedSources<Bench>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCaptured(result)
        // Bench は filler 一切なしなので、構築された annotation 2 件はいずれも 0-arg
        captured.size shouldBe 2
        // marker class name のみ確認 (Source filler が無いので value() を呼ぶ method は無い)
        (captured[0] as Annotation).annotationClass.java.simpleName shouldBe "Bench"
        (captured[1] as Annotation).annotationClass.java.simpleName shouldBe "Bench"
    }
})
