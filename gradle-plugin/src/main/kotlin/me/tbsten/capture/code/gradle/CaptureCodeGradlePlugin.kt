package me.tbsten.capture.code.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Capture Code Gradle plugin (`KotlinCompilerPluginSupportPlugin` 実装)。
 *
 * 責務:
 * - `:annotation:` runtime 依存の自動追加 (commonMain or implementation)
 * - [CaptureCodeExtension] (DSL) の登録
 * - [applyToCompilation] で DSL の値を `SubpluginOption` に変換し、CommandLineProcessor に渡す
 *
 * design `compiler-plugin-design.md` §5 Logic I 参照。
 */
public class CaptureCodeGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(CaptureCodeExtension.EXTENSION_NAME, CaptureCodeExtension::class.java)
        target.afterEvaluate {
            val hasKmp = target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val configName = if (hasKmp) "commonMainImplementation" else "implementation"
            target.dependencies.add(
                configName,
                "$GROUP_ID:annotation:$VERSION",
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
    }
}
