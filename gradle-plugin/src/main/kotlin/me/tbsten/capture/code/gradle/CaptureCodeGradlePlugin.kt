package me.tbsten.capture.code.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Capture Code Gradle plugin (`KotlinCompilerPluginSupportPlugin` 実装)。
 *
 * 責務:
 * - **Kotlin version guard** ([checkKotlinVersionOrFail]): project の Kotlin プラグイン
 *   バージョンが本 plugin のサポート範囲を逸脱していたら警告 / エラーで通知する。 戦略 B
 *   (compat module 分離) を採用しているため、 実 dispatch は ServiceLoader 経由で行われ、
 *   gradle-plugin は version 検出のみ責務を持つ。
 * - `:annotation:` runtime 依存の自動追加 (commonMain or implementation)
 * - [CaptureCodeExtension] (DSL) の登録
 * - [applyToCompilation] で DSL の値を `SubpluginOption` に変換し、CommandLineProcessor に渡す
 *
 * design `compiler-plugin-design.md` §5 Logic I 参照。
 */
public class CaptureCodeGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(CaptureCodeExtension.EXTENSION_NAME, CaptureCodeExtension::class.java)

        // ## IC fallback: marker set hash を KotlinCompile task input に attach
        //
        // impl-plan §4 リスク R5 (IC ON 状態で `@<Marker>` 付き宣言の追加 / 削除 / 編集が
        // caller 側 `capturedSources<T>()` に伝播しない) の根本解消。 task input が変われば
        // Gradle は task を非 up-to-date 扱いにし、 caller を含む module 全体が再 compile
        // される。 hash は Provider 経由で task 実行時に lazy 評価。
        //
        // configureEach + named-style `org.jetbrains.kotlin.gradle.tasks.KotlinCompile` を
        // 直接参照する。 KGP 必須の plugin なので runtime に kotlin-gradle-plugin が乗っている
        // 前提。 KGP 未 apply の場合は task type 自体が無いので何も attach されない (no-op)。
        attachMarkerHashTaskInput(target)

        target.afterEvaluate {
            // ## Kotlin version guard
            //
            // afterEvaluate 内で実施する: KGP の plugin version は project の build.gradle.kts で
            // `plugins { id("org.jetbrains.kotlin.jvm") version "..." }` の解決後に確定するため、
            // 最低でも afterEvaluate まで待つ必要がある。 KGP の `getKotlinPluginVersion(Project)`
            // extension は内部で project.extensions から version を取り出し、 取得できない (= KGP
            // が apply されていない) ならば null を返す。
            checkKotlinVersionOrFail(target)

            val hasKmp = target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val configName = if (hasKmp) "commonMainImplementation" else "implementation"
            target.dependencies.add(
                configName,
                "$GROUP_ID:annotation:$VERSION",
            )
        }
    }

    /**
     * project 内の全 `KotlinCompile` task に「marker set hash」 を input property
     * として attach する。 marker (= `@CaptureCode` meta-annotated class) または
     * その use site が増減 / 編集されると hash が変わり、 task が non-up-to-date
     * 扱いになって caller を含む module 全体が再 compile される。 これにより
     * `capturedSources<T>()` の rewrite が常に最新の marker world を反映する。
     *
     * 詳細は [MarkerSetHasher] 参照。
     */
    private fun attachMarkerHashTaskInput(target: Project) {
        val hashProvider = target.providers.provider { MarkerSetHasher.hashFor(target) }
        target.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach { task ->
            task.inputs.property(MARKER_HASH_INPUT_KEY, hashProvider)
        }
    }

    /**
     * project の Kotlin plugin バージョンを取り出し、 [SupportedKotlinVersions] に照らして
     * - `< MIN_SUPPORTED_VERSION` なら [GradleException] を throw して build を停止 (FIR / IR
     *   API が compat layer と互換でないため)
     * - `>= MAX_TESTED_VERSION_EXCLUSIVE` なら **logger.warn** で警告のみ出力 (まだ verify
     *   されていない新 version。 dispatch 自体は compat-k210 Factory が引き受ける見込み)
     * - その間 (サポート範囲内) なら **無音**
     *
     * KGP が apply されていない (= `getKotlinPluginVersion()` が null) や、 version 文字列が
     * parse 不能な場合は guard を skip し、 build を続行する (= silent fail を避ける)。
     */
    private fun checkKotlinVersionOrFail(target: Project) {
        val rawVersion = target.getKotlinPluginVersion()
        if (rawVersion.isNullOrBlank()) {
            // KGP 未 apply (= compiler plugin が attach されないので version guard も不要)。
            // CaptureCode plugin はそもそも KotlinCompilerPluginSupportPlugin なので KGP 必須だが、
            // `apply false` での明示 apply ケース等を考慮して silent skip にする。
            return
        }
        val current = KotlinVersionParts.parse(rawVersion)
        if (current == null) {
            target.logger.warn(
                "CaptureCode plugin: could not parse Kotlin plugin version '$rawVersion'. " +
                    "Version guard skipped. Plugin may not work as expected.",
            )
            return
        }
        val min = KotlinVersionParts.parse(SupportedKotlinVersions.MIN_SUPPORTED_VERSION)
            ?: error("Invalid MIN_SUPPORTED_VERSION: ${SupportedKotlinVersions.MIN_SUPPORTED_VERSION}")
        val maxExclusive = KotlinVersionParts.parse(SupportedKotlinVersions.MAX_TESTED_VERSION_EXCLUSIVE)
            ?: error("Invalid MAX_TESTED_VERSION_EXCLUSIVE: ${SupportedKotlinVersions.MAX_TESTED_VERSION_EXCLUSIVE}")

        if (current < min) {
            throw GradleException(
                "CaptureCode plugin requires Kotlin ${SupportedKotlinVersions.MIN_SUPPORTED_VERSION} or later, " +
                    "but the project is using Kotlin $rawVersion. " +
                    "Please upgrade the Kotlin Gradle plugin in your project.",
            )
        }
        if (current >= maxExclusive) {
            target.logger.warn(
                "CaptureCode plugin: Kotlin $rawVersion is newer than the latest verified version " +
                    "(< ${SupportedKotlinVersions.MAX_TESTED_VERSION_EXCLUSIVE}). " +
                    "The plugin may still work via the closest compatible compat layer, but is not officially " +
                    "supported on this version yet.",
            )
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = GROUP_ID,
        artifactId = "compiler-plugin",
        version = VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(CaptureCodeExtension::class.java)
        return project.provider {
            listOf(
                SubpluginOption(OPTION_INCLUDE_KDOC, extension.includeKdoc.toString()),
                SubpluginOption(OPTION_INCLUDE_IMPORTS, extension.includeImports.toString()),
                SubpluginOption(OPTION_INCLUDE_ANNOTATION_LINES, extension.includeAnnotationLines.toString()),
                SubpluginOption(OPTION_DEDENT, extension.dedent.toString()),
                SubpluginOption(OPTION_INCLUDE_LINE_INFO, extension.includeLineInfo.toString()),
            )
        }
    }

    private companion object {
        const val GROUP_ID = "me.tbsten.capture.code"
        const val PLUGIN_ID = "me.tbsten.capture.code"
        // TODO: バージョンは Gradle property または BuildConfig から注入する
        const val VERSION = "0.1.0-SNAPSHOT"

        // CommandLineProcessor 側 (`CaptureCodeCommandLineProcessor.OPTION_*`) と key 名を一致させる。
        // gradle-plugin は compiler-plugin に compileOnly 依存していないため、const 文字列を此処で再宣言する
        // (SSOT は compiler-plugin 側だが Gradle 側で参照すると classpath が重くなるため重複を許容)。
        const val OPTION_INCLUDE_KDOC = "includeKdoc"
        const val OPTION_INCLUDE_IMPORTS = "includeImports"
        const val OPTION_INCLUDE_ANNOTATION_LINES = "includeAnnotationLines"
        const val OPTION_DEDENT = "dedent"
        const val OPTION_INCLUDE_LINE_INFO = "includeLineInfo"

        // IC fallback: KotlinCompile task input property key. 名前は Gradle UP の
        // diagnostic 出力にもそのまま出るため、 plugin identity を含めておく。
        const val MARKER_HASH_INPUT_KEY = "captureCodeMarkerHash"
    }
}
