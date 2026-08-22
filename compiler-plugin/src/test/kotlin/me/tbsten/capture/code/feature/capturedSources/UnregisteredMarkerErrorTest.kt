package me.tbsten.capture.code.feature.capturedSources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.capture.code.CaptureCodeCompilerPluginRegistrar
import java.io.File

/**
 * bug-001: 「`@CaptureCode` meta-annotated な marker が type argument に指定されたのに、 その
 * declaration が今回の compilation unit に含まれていない (= marker registry 未登録)」 ケースの
 * compile ERROR (`CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED` /
 * `CC_CAPTUREDSOURCE_MARKER_NOT_REGISTERED`) の end-to-end 検証。
 *
 * ## 2 段 compile で incremental compile の stale round を再現する
 *
 * Kotlin incremental compile (IC) は変更 file だけを compiler に渡すため、
 * `capturedSources<T>()` を呼ぶ file だけが再 compile される round では marker declaration が
 * compile 対象から落ち、 marker は classpath の class file としてしか見えない。 kctfork では
 * これを
 *
 * 1. **1 段目**: plugin 無しで marker class だけを compile し、 output classes dir を得る
 * 2. **2 段目**: plugin 有り + 1 段目の output を `classpaths` に足して
 *    `capturedSources<ThatMarker>()` を compile する
 *
 * の 2 段 compile で再現する (別 module に marker を置いた構成とも等価)。 修正前はこの状況で
 * silent skip → runtime stub が残って実行時に
 * `IllegalStateException("CaptureCode compiler plugin is not applied")` だったが、 修正後は
 * compile ERROR で fail fast する。
 */
class UnregisteredMarkerErrorTest : FunSpec({

    /** plugin 付き compile helper (既存 test と同じ形 + 追加 classpath)。 */
    fun compileWithPlugin(vararg sources: SourceFile, classpath: List<File> = emptyList()): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(CaptureCodeCompilerPluginRegistrar())
            inheritClassPath = true
            classpaths = classpath
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /**
     * plugin 無し compile helper — marker class を 「別 compilation の成果物 (class file)」 として
     * 用意するために使う。 plugin を挟まないので marker registry には何も登録されない。
     */
    fun compileWithoutPlugin(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
        }.compile()

    /**
     * 1 段目でコンパイルする marker。 `@CaptureCode` は BINARY retention なので class file にも残る。
     *
     * 実際の IC round では marker は同一 module なので `internal` でも本 bug は起こるが、 kctfork の
     * 2 段 compile は module が別扱いになり internal 参照が resolution error になるため、 fixture では
     * visibility 修飾子を付けない (1 段目は plugin 無し compile なので Logic F の internal/private
     * 制約 check は掛からない)。
     */
    val externalMarkerSource = SourceFile.kotlin(
        "ExternalMarker.kt",
        """
        package example.lib

        import me.tbsten.capture.code.CaptureCode
        import me.tbsten.capture.code.Source

        @CaptureCode
        @Target(AnnotationTarget.FUNCTION)
        @Retention(AnnotationRetention.SOURCE)
        annotation class ExternalMarker(val source: Source = Source())
        """.trimIndent(),
    )

    test("別 compilation でコンパイル済みの marker を capturedSources の type argument にすると compile error になる") {
        val markerResult = compileWithoutPlugin(externalMarkerSource)
        markerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val result = compileWithPlugin(
            SourceFile.kotlin(
                "Caller.kt",
                """
                package example

                import example.lib.ExternalMarker
                import me.tbsten.capture.code.capturedSources

                internal object Main {
                    fun captured(): List<ExternalMarker> = capturedSources<ExternalMarker>()
                }
                """.trimIndent(),
            ),
            classpath = listOf(markerResult.outputDirectory),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "example.lib.ExternalMarker"
        result.messages shouldContain "its declaration is not part of"
        result.messages shouldContain "capturedSources<example.lib.ExternalMarker>() cannot be rewritten"
        result.messages shouldContain "stale incremental build"
        result.messages shouldContain "run a clean build"
    }

    test("marker not registered の error は呼び出し元の file 名と行番号を指す") {
        val markerResult = compileWithoutPlugin(externalMarkerSource)
        markerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val result = compileWithPlugin(
            SourceFile.kotlin(
                "LocatedCaller.kt",
                """
                package example

                import example.lib.ExternalMarker
                import me.tbsten.capture.code.capturedSources

                internal object Main {
                    // 呼び出しは 8 行目 (package 行を 1 行目として数える)
                    fun captured(): List<ExternalMarker> = capturedSources<ExternalMarker>()
                }
                """.trimIndent(),
            ),
            classpath = listOf(markerResult.outputDirectory),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        // location が付いていれば kctfork の message は `e: file://<path>/LocatedCaller.kt:8:44 ...`
        // の形になる。 file 名と行番号が両方出ていることを確認する (絶対 path は環境依存なので見ない)。
        val errorLine = result.messages.lines().first { it.contains("its declaration is not part of") }
        errorLine shouldContain "LocatedCaller.kt:8"
    }

    test("別 compilation でコンパイル済みの marker を capturedSource (単数版) に渡しても compile error になる") {
        val markerResult = compileWithoutPlugin(externalMarkerSource)
        markerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val result = compileWithPlugin(
            SourceFile.kotlin(
                "SingleCaller.kt",
                """
                package example

                import example.lib.ExternalMarker
                import me.tbsten.capture.code.capturedSource

                internal object Main {
                    fun captured(): ExternalMarker = capturedSource<ExternalMarker>()
                }
                """.trimIndent(),
            ),
            classpath = listOf(markerResult.outputDirectory),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "example.lib.ExternalMarker"
        result.messages shouldContain "its declaration is not part of"
        result.messages shouldContain "capturedSource<example.lib.ExternalMarker>() cannot be rewritten"
    }

    test("同じ未登録 marker への複数の capturedSources 呼び出しでも error は 1 回だけ report される") {
        val markerResult = compileWithoutPlugin(externalMarkerSource)
        markerResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val result = compileWithPlugin(
            SourceFile.kotlin(
                "TwoCallers.kt",
                """
                package example

                import example.lib.ExternalMarker
                import me.tbsten.capture.code.capturedSources

                internal object Main {
                    fun first(): List<ExternalMarker> = capturedSources<ExternalMarker>()
                    fun second(): List<ExternalMarker> = capturedSources<ExternalMarker>()
                }
                """.trimIndent(),
            ),
            classpath = listOf(markerResult.outputDirectory),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        // dedupe: 同一 marker FqN の error は最大 1 回 (RewriteCapturedSourcesCall の
        // unregisteredMarkerReportedFqns invariant)。
        val occurrences = result.messages.split("its declaration is not part of").size - 1
        occurrences shouldBe 1
    }

    test("同一 compilation に marker と site がある正常系は引き続き rewrite される") {
        val result = compileWithPlugin(
            SourceFile.kotlin(
                "SameCompilation.kt",
                """
                package example

                import me.tbsten.capture.code.CaptureCode
                import me.tbsten.capture.code.Source
                import me.tbsten.capture.code.capturedSources

                @CaptureCode
                @Target(AnnotationTarget.FUNCTION)
                @Retention(AnnotationRetention.SOURCE)
                internal annotation class LocalMarker(val source: Source = Source())

                @LocalMarker
                internal fun greet(): String = "hello"

                internal object Main {
                    fun captured(): List<LocalMarker> = capturedSources<LocalMarker>()
                }
                """.trimIndent(),
            ),
        )

        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        result.messages shouldNotContain "its declaration is not part of"

        val mainClass = result.classLoader.loadClass("example.Main")
        val mainInstance = mainClass.getField("INSTANCE").get(null)
        val captured = mainClass.getMethod("captured").invoke(mainInstance) as List<*>
        captured.size shouldBe 1
        val source = (captured.single() as Annotation).annotationClass.java
            .getMethod("source").invoke(captured.single()) as Annotation
        val value = source.annotationClass.java.getMethod("value").invoke(source) as String
        value shouldContain "internal fun greet()"
    }
})
