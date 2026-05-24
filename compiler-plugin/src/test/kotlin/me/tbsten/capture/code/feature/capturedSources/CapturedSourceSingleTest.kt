package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar

/**
 * `capturedSource<T>()` (単数版) の end-to-end 検証。
 *
 * 複数版 [capturedSources] と異なり、 サイト数が **ちょうど 1 件** であることを compile-time に
 * 強制する API。 0 件 / 複数件で **compile error** を出すことが特徴。
 */
class CapturedSourceSingleTest : FunSpec({

    fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /** marker instance から filler annotation を取り出す helper。 */
    fun fillerAnnotation(marker: Annotation, fillerMethodName: String): Annotation {
        val method = marker.annotationClass.java.getMethod(fillerMethodName)
        return method.invoke(marker) as Annotation
    }

    /** filler annotation の property (例: value / packageName) を取得する helper。 */
    fun annotationProperty(annotation: Annotation, propertyName: String): Any? {
        val method = annotation.annotationClass.java.getMethod(propertyName)
        return method.invoke(annotation)
    }

    /** capture サイトを返す `Main.captured()` を呼び出して **annotation instance 1 つ** を取得する helper。 */
    fun loadCapturedSingle(result: JvmCompilationResult, mainFqn: String = "example.Main"): Annotation {
        val mainClass = result.classLoader.loadClass(mainFqn)
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        return mainClass.getMethod("captured").invoke(mainInstance) as Annotation
    }

    // ----------------------------------------------------------------
    // 1. happy path — class annotation
    // ----------------------------------------------------------------
    test("class declaration: capturedSource<T>() returns the single marker instance") {
        val result = compile(
            SourceFile.kotlin(
                "AppEntryPoint.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class AppEntryPoint(val source: Source = Source())

                @AppEntryPoint
                internal class MyApp

                internal object Main {
                    fun captured(): AppEntryPoint = capturedSource<AppEntryPoint>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCapturedSingle(result)
        val source = fillerAnnotation(captured, "source")
        annotationProperty(source, "value") shouldBe "internal class MyApp"
    }

    // ----------------------------------------------------------------
    // 2. happy path — property annotation
    // ----------------------------------------------------------------
    test("property declaration: capturedSource<T>() returns the single marker instance") {
        val result = compile(
            SourceFile.kotlin(
                "Snippets.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class Snippets(val source: Source = Source())

                @Snippets
                internal val greeting = "hello"

                internal object Main {
                    fun captured(): Snippets = capturedSource<Snippets>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCapturedSingle(result)
        val source = fillerAnnotation(captured, "source")
        (annotationProperty(source, "value") as String).isNotEmpty() shouldBe true
    }

    // ----------------------------------------------------------------
    // 3. 0 件 — no site → compile ERROR
    // ----------------------------------------------------------------
    test("zero sites: capturedSource<T>() reports CC_CAPTUREDSOURCE_NO_SITE compile error") {
        val result = compile(
            SourceFile.kotlin(
                "NoSiteMarker.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class NoSiteMarker(val source: Source = Source())

                internal object Main {
                    fun captured(): NoSiteMarker = capturedSource<NoSiteMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "No site found"
        result.messages shouldContain "example.NoSiteMarker"
    }

    // ----------------------------------------------------------------
    // 4. 複数件 (2件) → compile ERROR
    // ----------------------------------------------------------------
    test("two sites: capturedSource<T>() reports CC_CAPTUREDSOURCE_MULTIPLE_SITES compile error") {
        val result = compile(
            SourceFile.kotlin(
                "TwoSites.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DuplicateMarker(val source: Source = Source())

                @DuplicateMarker
                internal class A

                @DuplicateMarker
                internal class B

                internal object Main {
                    fun captured(): DuplicateMarker = capturedSource<DuplicateMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "Multiple sites"
        result.messages shouldContain "example.DuplicateMarker"
    }

    // ----------------------------------------------------------------
    // 5. 複数件 (3件以上) → compile ERROR (location 3 件すべて)
    // ----------------------------------------------------------------
    test("three sites: capturedSource<T>() reports multiple sites with all locations") {
        val result = compile(
            SourceFile.kotlin(
                "ThreeSites.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class TripleMarker(val source: Source = Source())

                @TripleMarker
                internal class A

                @TripleMarker
                internal class B

                @TripleMarker
                internal class C

                internal object Main {
                    fun captured(): TripleMarker = capturedSource<TripleMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "Multiple sites found"
        result.messages shouldContain "example.TripleMarker"
        // fixture の trimIndent 後の line position に基づき、 各 site の `file:line` 部分文字列を
        // 直接 assert する (= `ThreeSites.kt:12`, `:15`, `:18`)。 declaration の startLine は
        // collector が `@TripleMarker` annotation 行まで遡らせるため annotation 行 = `@TripleMarker`
        // の行になる (FillerBuilderTest と同じ semantics)。
        result.messages shouldContain "ThreeSites.kt:12"
        result.messages shouldContain "ThreeSites.kt:15"
        result.messages shouldContain "ThreeSites.kt:18"
    }

    // ----------------------------------------------------------------
    // 6. T が @CaptureCode なし → FIR ERROR (既存 ValidateCapturedSourcesCall に統合)
    // ----------------------------------------------------------------
    test("T without @CaptureCode meta-annotation produces COMPILATION_ERROR") {
        val result = compile(
            SourceFile.kotlin(
                "NotAMarker.kt",
                """
                package example

                import me.tbsten.capture.code.capturedSource

                @Retention(AnnotationRetention.SOURCE)
                annotation class NotAMarker

                internal object Main {
                    fun captured(): NotAMarker = capturedSource<NotAMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "must be annotated with @CaptureCode"
        result.messages shouldContain "example.NotAMarker"
    }

    // ----------------------------------------------------------------
    // 7. T が type parameter → FIR WARNING (既存 BUG-H provisional warn)
    // ----------------------------------------------------------------
    test("T as type parameter produces CC_CAPTUREDSOURCES_T_IS_TYPE_PARAMETER warning") {
        val result = compile(
            SourceFile.kotlin(
                "TypeParam.kt",
                """
                package example

                import me.tbsten.capture.code.capturedSource

                internal inline fun <reified T : Annotation> getOne(): T = capturedSource<T>()
                """.trimIndent(),
            ),
        )
        // type-parameter は warning なので compile 自体は通る (exitCode は OK)
        // ただし IR phase でも `capturedSource` の rewrite を試みた結果、 marker 未解決で
        // 元の call が残り、 runtime stub が走る (= 既存複数版と同じ振る舞い)。
        // ここでは warning 文面の存在のみ確認する。
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldContain "cannot be rewritten when T is a type parameter"
        // 単数版固有の compile error (NO_SITE / MULTIPLE_SITES) は type-parameter ケースでは
        // marker FqN を抽出できないため一切発火しない。 IR phase が `markerFqnOf` の null fallback
        // で silent skip する経路の保護網。
        result.messages shouldNotContain "No site found"
        result.messages shouldNotContain "Multiple sites"
    }

    // ----------------------------------------------------------------
    // 8. 既存複数版 (capturedSources) は影響なし
    // ----------------------------------------------------------------
    test("existing capturedSources<T>() coexists with capturedSource<T>() without interference") {
        val result = compile(
            SourceFile.kotlin(
                "Both.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class BothMarker(val source: Source = Source())

                @BothMarker
                internal class Solo

                internal object Main {
                    fun single(): BothMarker = capturedSource<BothMarker>()
                    fun many(): List<BothMarker> = capturedSources<BothMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        // 既存複数版の checker メッセージは混入しない
        result.messages shouldNotContain "No site found"
        result.messages shouldNotContain "Multiple sites"

        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val single = mainClass.getMethod("single").invoke(mainInstance) as Annotation
        val many = mainClass.getMethod("many").invoke(mainInstance) as List<*>
        // 単数版は marker instance を直接返す
        annotationProperty(fillerAnnotation(single, "source"), "value") shouldBe "internal class Solo"
        // 複数版は同じ marker を 1 件の List として返す
        many.size shouldBe 1
        annotationProperty(fillerAnnotation(many[0] as Annotation, "source"), "value") shouldBe "internal class Solo"
    }

    // ----------------------------------------------------------------
    // 9. SourceLocation filler が単数版でも埋まる
    // ----------------------------------------------------------------
    test("SourceLocation filler is populated for single-site capturedSource<T>()") {
        val result = compile(
            SourceFile.kotlin(
                "LocSingle.kt",
                """
                package example.loc

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.SourceLocation
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.PROPERTY)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LocSingle(val location: SourceLocation = SourceLocation())

                @LocSingle
                internal val flag = true

                internal object Main {
                    fun captured(): LocSingle = capturedSource<LocSingle>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val captured = loadCapturedSingle(result, mainFqn = "example.loc.Main")
        val location = fillerAnnotation(captured, "location")
        annotationProperty(location, "packageName") shouldBe "example.loc"
        val startLine = annotationProperty(location, "startLine") as Int
        (startLine > 0) shouldBe true
    }

    // ----------------------------------------------------------------
    // 10. 戻り値型が T そのもの (= type safety)
    //
    // `capturedSource<AppEntryPoint>()` が `AppEntryPoint` 型として変数代入できることを compile
    // できることで間接的に確認する (type safety は Kotlin compiler が保証する範囲)。
    // ----------------------------------------------------------------
    test("return type of capturedSource<T>() is T (assignable to val of type T)") {
        val result = compile(
            SourceFile.kotlin(
                "TypeReturn.kt",
                """
                package example.ret

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class TypeMarker(val source: Source = Source())

                @TypeMarker
                internal class Hit

                internal object Main {
                    fun captured(): TypeMarker {
                        val x: TypeMarker = capturedSource<TypeMarker>()
                        return x
                    }
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCapturedSingle(result, mainFqn = "example.ret.Main")
        annotationProperty(fillerAnnotation(captured, "source"), "value") shouldBe "internal class Hit"
    }

    // ----------------------------------------------------------------
    // 11. dedupe — 同 marker FqN について複数 call が NO_SITE なら ERROR は 1 回だけ
    //
    // RewriteCapturedSourceCall.reportedFqns invariant の保護網。
    // 「marker FqN ごとに ERROR 発火は最大 1 回」 を 0 件 ケースで担保する。
    // ----------------------------------------------------------------
    test("dedup: same marker called from multiple sites emits NO_SITE error only once") {
        val result = compile(
            SourceFile.kotlin(
                "Dedup.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class DedupMarker(val source: Source = Source())

                internal object Main {
                    fun a(): DedupMarker = capturedSource<DedupMarker>()
                    fun b(): DedupMarker = capturedSource<DedupMarker>()
                    fun c(): DedupMarker = capturedSource<DedupMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        // 3 call すべて 0 件 だが、 NO_SITE ERROR は marker FqN 1 つに対して 1 度のみ発火する。
        // `''@example.DedupMarker''` 部分文字列を call 数だけ split して occurrences 数で確認。
        val occurrences = result.messages.split("No site found for '@example.DedupMarker'").size - 1
        occurrences shouldBe 1
    }

    // ----------------------------------------------------------------
    // 12. file annotation site — `@file:Marker` 起源でも単数版 capturedSource<T>() が成立する
    //
    // FileAnnotationTest (plural 版) の case 2 と対称。 file annotation 1 件のみが対象なら
    // capturedSource<T>() は問題なく rewrite され、 CaptureKind.value == FILE になる。
    // ----------------------------------------------------------------
    test("file annotation site: capturedSource<T>() returns the file site instance") {
        val result = compile(
            SourceFile.kotlin(
                "FileAnno.kt",
                """
                @file:FileLevelMarker

                package example.file

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.CaptureKind
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.FILE)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class FileLevelMarker(val kind: CaptureKind = CaptureKind())

                internal object Main {
                    fun captured(): FileLevelMarker = capturedSource<FileLevelMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCapturedSingle(result, mainFqn = "example.file.Main")
        val kindFiller = fillerAnnotation(captured, "kind")
        val kindValue = annotationProperty(kindFiller, "value") as Enum<*>
        kindValue.name shouldBe "FILE"
    }

    // ----------------------------------------------------------------
    // 13. user-defined parameter — @Marker(id = X) が単数版でも保たれる
    //
    // UserArgIrBuilderTest (plural 版) の Int parameter case と対称。 declaration 起源での
    // `@UserArgMarker(id = 42)` の値が rewrite 結果に保持されることを確認する。
    // `AnnotationRetention.RUNTIME` を使うのは reflection で id を読むため (SOURCE だと runtime
    // に保持されない)。
    // ----------------------------------------------------------------
    test("user-defined parameter: capturedSource<T>() preserves user-supplied argument") {
        val result = compile(
            SourceFile.kotlin(
                "UserArg.kt",
                """
                package example.userarg

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSource

                @CaptureCode
                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                internal annotation class UserArgMarker(
                    val id: Int = -1,
                    val source: Source = Source(),
                )

                @UserArgMarker(id = 42)
                internal class Hit

                internal object Main {
                    fun captured(): UserArgMarker = capturedSource<UserArgMarker>()
                }
                """.trimIndent(),
            ),
        )
        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        val captured = loadCapturedSingle(result, mainFqn = "example.userarg.Main")
        annotationProperty(captured, "id") shouldBe 42
    }
})
