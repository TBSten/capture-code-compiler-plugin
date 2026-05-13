package me.tbsten.capture.code.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class CaptureCodeGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
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
        return kotlinCompilation.target.project.provider { emptyList() }
    }

    private companion object {
        const val GROUP_ID = "me.tbsten.capture.code"
        const val PLUGIN_ID = "me.tbsten.capture.code"
        // TODO: バージョンは Gradle property または BuildConfig から注入する
        const val VERSION = "0.1.0-SNAPSHOT"
    }
}
