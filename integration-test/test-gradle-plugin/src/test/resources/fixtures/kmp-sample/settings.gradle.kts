// ----------------------------------------------------------------------------
// KMP fixture for true E2E verification via TestKit + includeBuild
//
// 本 fixture は `integration-test/test-gradle-plugin/src/test/kotlin/.../
// KmpE2eTest.kt` から GradleRunner で起動される独立した Gradle project。 root
// project (capture-code-compiler-plugin 本体) を `includeBuild` 経由で取り込み、
// `dependencySubstitution` で coordinate を解決する。
//
// root への相対パス計算:
//   kmp-sample (現在地)
//   .. = fixtures
//   ../.. = resources
//   ../../.. = test
//   ../../../.. = src
//   ../../../../.. = test-gradle-plugin
//   ../../../../../.. = integration-test
//   ../../../../../../.. = root
//
// 構成は `jvm-sample` と同じパターン (`includeBuild("../../../../../../..")` を 2 重
// に書いて plugin 解決と dependency substitution を両立) を踏襲する。 違いは:
//   - `kotlin("multiplatform")` を pluginManagement で resolve
//   - `:annotation` の jvm variant (`annotation-jvm`) と root publication coordinate
//     `annotation` の両方を本 project に substitute
//
// この fixture では task-040 で scope 外にした「KMP project に `plugins { id(
// "me.tbsten.capture.code") }` 経由で plugin を attach し、 commonTest 起点の
// `capturedSources<T>()` が jvmTest compile で正しく rewrite される」ことを真の
// E2E で verify する。
// ----------------------------------------------------------------------------
pluginManagement {
    includeBuild("../../../../../../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val kotlinVersion: String =
            System.getProperty("test-gradle-plugin.kotlinVersion")
                ?: providers.gradleProperty("test-gradle-plugin.kotlinVersion").orNull
                ?: "2.0.21"
        kotlin("multiplatform") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

includeBuild("../../../../../../..") {
    dependencySubstitution {
        // KMP の場合、 `commonMain` / `commonTest` 由来は root publication coordinate
        // (`annotation`) を要求し、 jvm runtime classpath は platform-specific
        // coordinate (`annotation-jvm`) を要求するため、 両方を本 project に向ける。
        substitute(module("me.tbsten.capture.code:annotation"))
            .using(project(":annotation"))
        substitute(module("me.tbsten.capture.code:annotation-jvm"))
            .using(project(":annotation"))
    }
}

rootProject.name = "kmp-sample"
