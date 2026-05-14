package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.gradle.testfixtures.ProjectBuilder
import java.io.File

/**
 * [MarkerSetHasher] の sanity test。
 *
 * R5 fallback の核となる「marker 集合変化を hash で検出する」 ロジック単体を
 * verify する。 ProjectBuilder で `projectDir` を temp に置き、 `src/main/kotlin`
 * 配下に各種ファイルを物理的に書き出して hash の値を比較する。
 *
 * `@CaptureCode` meta-annotated class とその use site の追加 / 削除 / 編集が
 * いずれも hash を変化させること、 逆に無関係なファイル変更は hash を変えない
 * こと (= false-positive で IC を殺さない最低限) を保証する。
 */
class MarkerSetHasherTest : StringSpec({

    /** project + temp `src/main/kotlin/<pkg>` ディレクトリを返す。 */
    fun newProjectWithSrcRoot(): Pair<org.gradle.api.Project, File> {
        val project = ProjectBuilder.builder().build()
        val srcRoot = File(project.projectDir, "src/main/kotlin/app")
        srcRoot.mkdirs()
        return project to srcRoot
    }

    "marker が一つも無い project では sentinel hash を返す" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Plain.kt").writeText(
            """
            package app
            fun hello() = "hi"
            """.trimIndent(),
        )
        val hash = MarkerSetHasher.hashFor(project)
        // 中立 sentinel (= 任意 task input 値) であれば充分。 値は安定していれば OK。
        hash.shouldNotBeBlank()
    }

    "marker class の追加で hash が変化する" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Plain.kt").writeText("package app\nfun hello() = 1\n")

        val before = MarkerSetHasher.hashFor(project)

        // `@CaptureCode` meta-annotated marker class を追加。
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )

        val after = MarkerSetHasher.hashFor(project)
        after shouldNotBe before
    }

    "marker use site の追加で hash が変化する" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "foo"
            """.trimIndent(),
        )
        val before = MarkerSetHasher.hashFor(project)

        // 新しい use site を追加。
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "foo"
            @Snippet fun bar() = "bar"
            """.trimIndent(),
        )
        val after = MarkerSetHasher.hashFor(project)
        after shouldNotBe before
    }

    "marker use site の本文編集で hash が変化する" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "Hello!"
            """.trimIndent(),
        )
        val before = MarkerSetHasher.hashFor(project)

        // 本文だけ書き換え。
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "Konnichiwa!"
            """.trimIndent(),
        )
        val after = MarkerSetHasher.hashFor(project)
        after shouldNotBe before
    }

    "marker use site の削除で hash が変化する" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )
        val usage = File(srcRoot, "Usage.kt")
        usage.writeText(
            """
            package app
            @Snippet fun foo() = "foo"
            """.trimIndent(),
        )
        val before = MarkerSetHasher.hashFor(project)

        usage.delete()
        val after = MarkerSetHasher.hashFor(project)
        after shouldNotBe before
    }

    "marker と無関係なファイル変更では hash が変化しない" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "foo"
            """.trimIndent(),
        )
        val unrelated = File(srcRoot, "Plain.kt")
        unrelated.writeText("package app\nfun greet() = 1\n")
        val before = MarkerSetHasher.hashFor(project)

        // marker でも use site でもないファイルを編集。
        unrelated.writeText("package app\nfun greet() = 42\n")
        val after = MarkerSetHasher.hashFor(project)

        // hash は marker world に依存するため、 無関係ファイルでは変化しないのが望ましい
        // (IC を必要以上に殺さない for performance)。
        after shouldBe before
    }

    "同じ marker world では hash が決定的 (run 間で安定)" {
        val (project, srcRoot) = newProjectWithSrcRoot()
        File(srcRoot, "Marker.kt").writeText(
            """
            package app
            import me.tbsten.capture.code.CaptureCode
            @CaptureCode
            annotation class Snippet
            """.trimIndent(),
        )
        File(srcRoot, "Usage.kt").writeText(
            """
            package app
            @Snippet fun foo() = "foo"
            """.trimIndent(),
        )
        val first = MarkerSetHasher.hashFor(project)
        val second = MarkerSetHasher.hashFor(project)
        first shouldBe second
    }
})
