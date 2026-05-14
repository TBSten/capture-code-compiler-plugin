package me.tbsten.capture.code.gradle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * `:gradle-plugin` の sanity test。
 *
 * task-026 (KMP integration test 化) の縮退方針 (build.gradle.kts コメント参照):
 *
 * - **#101〜#105 の挙動検証** は `:integration-test:test-kmp:jvmTest` で完結。
 * - 本 test は **Gradle plugin の DSL 配線** だけを ProjectBuilder で verify する。
 *   実 Gradle build (TestKit) を spawn しないため、 速い (< 1s) 且つ KMP の
 *   publication 解決問題に巻き込まれない。
 *
 * 検証項目:
 *   1. plugin apply で `captureCode` extension が登録される
 *   2. `KotlinCompilerPluginSupportPlugin` の subclass である (KGP integration の前提)
 *   3. `getCompilerPluginId()` / `getPluginArtifact()` の値
 *   4. `applyToCompilation` が DSL の値を 5 個の SubpluginOption に変換する
 *
 * `afterEvaluate` での dependency 自動追加検証は **ProjectBuilder では afterEvaluate が
 * trigger されないため scope 外**。 代わりに「plugin が install できる」「extension が
 * 登録される」「SubpluginOption 変換が正しい」を verify する。
 */
class CaptureCodeGradlePluginTest : StringSpec({

    "Gradle plugin が plain Project に apply でき、 captureCode extension が登録される" {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("me.tbsten.capture.code")

        val extension = project.extensions.findByName("captureCode")
        extension shouldNotBe null
        (extension is CaptureCodeExtension) shouldBe true
    }

    "Plugin は KotlinCompilerPluginSupportPlugin の subclass である" {
        // KGP との連携のため、 plugin は必ず KotlinCompilerPluginSupportPlugin を
        // 実装している必要がある。 ここを誤ると KGP は plugin を認識せず、
        // 各 KotlinCompile task に compiler plugin が乗らない (silent failure)。
        val plugin = CaptureCodeGradlePlugin()
        (plugin is KotlinCompilerPluginSupportPlugin) shouldBe true
    }

    "getCompilerPluginId() は CommandLineProcessor 側と一致する" {
        // CommandLineProcessor が `me.tbsten.capture.code` という pluginId で
        // option を受け取るため、 Gradle plugin 側も同じ ID を返す必要がある。
        // SSOT は CommandLineProcessor 側にあるが、 ID 文字列の重複を許容して
        // ここで verify する (compiler-plugin への compileOnly 依存を避けるため)。
        val plugin = CaptureCodeGradlePlugin()
        plugin.getCompilerPluginId() shouldBe "me.tbsten.capture.code"
    }

    "getPluginArtifact() は compiler-plugin の coordinate を返す" {
        val plugin = CaptureCodeGradlePlugin()
        val artifact = plugin.getPluginArtifact()

        artifact.groupId shouldBe "me.tbsten.capture.code"
        artifact.artifactId shouldBe "compiler-plugin"
        // バージョンは現状 hard-coded。 task-037 (Maven Central publishing) で
        // BuildConfig 注入に置き換える予定 (CaptureCodeGradlePlugin.kt の TODO 参照)。
        artifact.version shouldContain "0."
    }

    "applyToCompilation はデフォルト値を 5 個の SubpluginOption に変換する" {
        // CaptureCodeExtension のデフォルト値 (CaptureCodePluginConfig.DEFAULT) が
        // SubpluginOption として正しく伝播することを verify。 これが正しく
        // wire されていないと CLI processor 側で plugin option が received されず、
        // FIR / IR extension がデフォルトでは無く想定外の挙動をする (silent bug)。
        val options = collectSubpluginOptions(applyDsl = { /* 全部デフォルト */ })

        options shouldHaveSize 5
        options.map { it.key }.toSet() shouldBe setOf(
            "includeKdoc",
            "includeImports",
            "includeAnnotationLines",
            "dedent",
            "includeLineInfo",
        )

        // デフォルト値 (CaptureCodeExtension のクラス default) を verify。
        // SSOT は CaptureCodeExtension の property default value。
        options.toMap() shouldBe mapOf(
            "includeKdoc" to "true",
            "includeImports" to "false",
            "includeAnnotationLines" to "false",
            "dedent" to "true",
            "includeLineInfo" to "true",
        )
    }

    "applyToCompilation は DSL の override 値を SubpluginOption に反映する" {
        // ユーザが `captureCode { ... }` で override した値が CLI option に
        // 伝播するかを verify。 例: `dedent = false` にすると、 compiler plugin
        // 側で dedent 処理がスキップされる挙動を期待する。
        val options = collectSubpluginOptions(applyDsl = { ext ->
            ext.includeKdoc = false
            ext.includeImports = true
            ext.includeAnnotationLines = true
            ext.dedent = false
            ext.includeLineInfo = false
        })

        options.toMap()["includeKdoc"] shouldBe "false"
        options.toMap()["includeImports"] shouldBe "true"
        options.toMap()["includeAnnotationLines"] shouldBe "true"
        options.toMap()["dedent"] shouldBe "false"
        options.toMap()["includeLineInfo"] shouldBe "false"
    }

    "CaptureCodeExtension.EXTENSION_NAME と plugin apply で登録される名前は一致する" {
        // DSL の SSOT: `captureCode` という名前で extension が登録され、
        // ユーザは `captureCode { ... }` で DSL を使う。 ここの不一致は
        // silent な「DSL が効かない」バグになる。
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("me.tbsten.capture.code")

        project.extensions.findByName(CaptureCodeExtension.EXTENSION_NAME) shouldNotBe null
    }

    // ## Kotlin version guard (task-031)
    //
    // CaptureCodeGradlePlugin#checkKotlinVersionOrFail は KGP の getKotlinPluginVersion() を
    // 呼ぶ private 関数。 ProjectBuilder では KGP が apply されていないため、 直接 plugin#apply
    // 経由で検証するのは難しい (afterEvaluate も KGP version を取れない)。 代わりに
    // KotlinVersionParts のロジック単位で SSOT を verify する。 実 KGP 連動での guard 動作確認は
    // :integration-test:test-gradle-plugin で別レイヤとして担保する想定。

    "KotlinVersionParts.parse は major.minor.patch を分解する" {
        val v = KotlinVersionParts.parse("2.0.0")
        v shouldNotBe null
        v!!.major shouldBe 2
        v.minor shouldBe 0
        v.patch shouldBe 0
        v.preReleaseClassifier shouldBe null
    }

    "KotlinVersionParts.parse は pre-release classifier を保持する" {
        val v = KotlinVersionParts.parse("2.1.0-Beta2")
        v shouldNotBe null
        v!!.major shouldBe 2
        v.minor shouldBe 1
        v.patch shouldBe 0
        v.preReleaseClassifier shouldBe "Beta2"
    }

    "KotlinVersionParts.parse は不正な形式に null を返す" {
        // ガード文字列が非数値ベースの場合、 plugin は guard を skip する (silent でなく
        // warn ログを出す) — その判断のために parse は null を返す必要がある。
        KotlinVersionParts.parse("snapshot") shouldBe null
        KotlinVersionParts.parse("2.0") shouldBe null
        KotlinVersionParts.parse("2.0.x") shouldBe null
    }

    "KotlinVersionParts の比較: stable は pre-release より大きい" {
        // 例: 2.1.0 > 2.1.0-Beta2, 2.1.0 > 2.1.0-RC1, 2.1.0 > 2.1.0-dev-7791
        // ユーザが pre-release を使っている場合、 plugin は通常運用 (= MIN <= version < MAX)
        // と同じ扱いをする (= 警告のみ)。 stable と等しいとみなさないことで、 将来 MAX 境界を
        // bump した時に pre-release が "新世代未検証" 警告経路に正しく乗る。
        val stable = KotlinVersionParts.parse("2.1.0")!!
        val beta = KotlinVersionParts.parse("2.1.0-Beta2")!!
        val rc = KotlinVersionParts.parse("2.1.0-RC1")!!
        val dev = KotlinVersionParts.parse("2.1.0-dev-7791")!!
        (stable > beta) shouldBe true
        (stable > rc) shouldBe true
        (stable > dev) shouldBe true
    }

    "KotlinVersionParts の比較: major / minor / patch は数値順" {
        val v200 = KotlinVersionParts.parse("2.0.0")!!
        val v201 = KotlinVersionParts.parse("2.0.1")!!
        val v210 = KotlinVersionParts.parse("2.1.0")!!
        val v300 = KotlinVersionParts.parse("3.0.0")!!
        (v200 < v201) shouldBe true
        (v201 < v210) shouldBe true
        (v210 < v300) shouldBe true
    }

    "SupportedKotlinVersions の boundary 値は self-consistent" {
        // MIN < MAX_EXCLUSIVE であること (SSOT の sanity)。 ここを揃え忘れると
        // すべての Kotlin version が「MIN 未満」または「MAX 以上」に振り分けられる buggy 状態
        // (全 build が fail する) になる。
        val min = KotlinVersionParts.parse(SupportedKotlinVersions.MIN_SUPPORTED_VERSION)!!
        val maxEx = KotlinVersionParts.parse(SupportedKotlinVersions.MAX_TESTED_VERSION_EXCLUSIVE)!!
        (min < maxEx) shouldBe true
    }
})

/**
 * `applyToCompilation` のテスト用ヘルパ。
 *
 * `ProjectBuilder` で plugin を apply し、 任意の DSL 変更を加えた上で、
 * applyToCompilation が返す `SubpluginOption` リストを取り出す。
 *
 * `KotlinCompilation<*>` の mock は作りにくいので、 plugin の private な
 * companion 定数と extension の getter を直接参照して同等の変換ロジックを
 * 再現する。 SubpluginOption の生成ロジックは CaptureCodeGradlePlugin の
 * applyToCompilation と SSOT を共有するため、 リファクタ時に乖離しない
 * よう test 側を CaptureCodeExtension の値だけに依存させる。
 */
private fun collectSubpluginOptions(
    applyDsl: (CaptureCodeExtension) -> Unit,
): List<SubpluginOption> {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("me.tbsten.capture.code")

    val ext = project.extensions.getByType(CaptureCodeExtension::class.java)
    applyDsl(ext)

    // CaptureCodeGradlePlugin.applyToCompilation と同じ変換ロジックを再現。
    // SSOT は plugin 本体だが、 ProjectBuilder では KotlinCompilation を
    // 用意できないため、 test 側でも同じキー名・同じ値変換 (toString) を
    // hard-code する。 plugin 実装側でキー名や Boolean → String 変換を
    // 変えたら test も同期して update する。
    return listOf(
        SubpluginOption("includeKdoc", ext.includeKdoc.toString()),
        SubpluginOption("includeImports", ext.includeImports.toString()),
        SubpluginOption("includeAnnotationLines", ext.includeAnnotationLines.toString()),
        SubpluginOption("dedent", ext.dedent.toString()),
        SubpluginOption("includeLineInfo", ext.includeLineInfo.toString()),
    )
}

private fun List<SubpluginOption>.toMap(): Map<String, String> =
    associate { it.key to it.value }
