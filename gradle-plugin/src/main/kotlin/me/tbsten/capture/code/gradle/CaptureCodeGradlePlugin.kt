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
        val extension =
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

        // ## IC 無効化 (bug-001): marker registry は「今回の compile 単位」しか見えない
        //
        // Kotlin IC は変更 file (+ ABI 依存 file) だけを compiler に渡す。 caller file
        // (`capturedSources<T>()` を呼ぶ file) だけが再 compile される round では marker
        // registry / site 収集が空になり、 rewrite が乗らない runtime stub や stale capture
        // が class file に残る。 MarkerSetHasher は marker world の変化しか検知できないため、
        // 既定では compile task の incremental compilation 自体を無効化して正しさを優先する。
        disableIncrementalCompilation(target, extension)

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
     * plugin を apply した module の Kotlin compile task の incremental compilation (IC) を
     * 無効化する ([CaptureCodeExtension.disableIncrementalCompilation] = true の既定時)。
     *
     * ## なぜ IC を殺すのか (bug-001)
     *
     * Kotlin IC は **変更された file (+ その ABI 依存 file) だけ** を compiler に渡す。 この
     * plugin の marker registry (FIR) / site 収集 (IR) は「今回 compiler に渡された source」
     * しか見えないため、 例えば `capturedSources<T>()` を呼ぶ file に空行を 1 行足しただけの
     * round では marker registry が空になり、 rewrite されなかった runtime stub が class file
     * に残って実行時に `IllegalStateException` になる (site 編集だけの round では stale capture)。
     * [MarkerSetHasher] (R5 fallback) は marker world の変化しか hash に反映しないので、
     * caller file 単独の編集はすり抜ける。 そのため task 単位で `incremental = false` を強制する。
     *
     * ## 対象 task
     *
     * `AbstractKotlinCompile` (JVM の `KotlinCompile` / JS の `Kotlin2JsCompile` が継承) の
     * `incremental` var を落とす。 Native (`KotlinNativeCompile`) は `AbstractKotlinCompile`
     * 系でなく該当 var も無いため対象外 (Kotlin/Native の compile は per-module 全量 compile)。
     *
     * ## 登録タイミング
     *
     * KGP は task 登録時の `taskProvider.configure { task.incremental = ... }` で default を
     * 代入する。 configure action は登録順に実行されるため、
     *
     * - apply 時の `configureEach` — 通常の plugins 順 (`kotlin("jvm")` → 本 plugin) では
     *   KGP の default 代入より後に並ぶのでこれだけで足りる
     * - `afterEvaluate` での再登録 — 本 plugin を `kotlin("jvm")` より先に書いた場合でも
     *   KGP の default 代入より後に並ぶことを保証する (idempotent な二重代入なので無害)
     *
     * の 2 段構えにする。 flag は configure action 実行時に読むので、 `captureCode { }` DSL
     * での opt-out (`disableIncrementalCompilation = false`) が反映される。
     */
    private fun disableIncrementalCompilation(target: Project, extension: CaptureCodeExtension) {
        val disableIfEnabled: (org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile<*>) -> Unit = { task ->
            if (extension.disableIncrementalCompilation) {
                task.incremental = false
            }
        }
        target.tasks.withType(org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile::class.java)
            .configureEach(disableIfEnabled)
        target.afterEvaluate {
            target.tasks.withType(org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile::class.java)
                .configureEach(disableIfEnabled)
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
                SubpluginOption(OPTION_WARN_ON_EMPTY_CAPTURE, extension.warnOnEmptyCapture.toString()),
            )
        }
    }

    private companion object {
        const val GROUP_ID = "me.tbsten.capture.code"
        const val PLUGIN_ID = "me.tbsten.capture.code"
        // 自動生成された const (build.gradle.kts: generatePluginVersion task)。
        // SSOT は root の gradle.properties の VERSION_NAME。
        val VERSION: String = CAPTURE_CODE_PLUGIN_VERSION

        // CommandLineProcessor 側 (`CaptureCodeCommandLineProcessor.OPTION_*`) と key 名を一致させる。
        // gradle-plugin は compiler-plugin に compileOnly 依存していないため、const 文字列を此処で再宣言する
        // (SSOT は compiler-plugin 側だが Gradle 側で参照すると classpath が重くなるため重複を許容)。
        const val OPTION_INCLUDE_KDOC = "includeKdoc"
        const val OPTION_INCLUDE_IMPORTS = "includeImports"
        const val OPTION_INCLUDE_ANNOTATION_LINES = "includeAnnotationLines"
        const val OPTION_DEDENT = "dedent"
        const val OPTION_INCLUDE_LINE_INFO = "includeLineInfo"
        const val OPTION_WARN_ON_EMPTY_CAPTURE = "warnOnEmptyCapture"

        // IC fallback: KotlinCompile task input property key. 名前は Gradle UP の
        // diagnostic 出力にもそのまま出るため、 plugin identity を含めておく。
        const val MARKER_HASH_INPUT_KEY = "captureCodeMarkerHash"
    }
}
