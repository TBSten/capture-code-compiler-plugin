// ----------------------------------------------------------------------------
// JVM-only fixture for true E2E verification via TestKit + includeBuild
//
// 本 fixture は `integration-test/test-gradle-plugin/src/test/kotlin/.../
// CaptureCodeGradlePluginE2eTest.kt` から GradleRunner で起動される独立した
// Gradle project。 root project (capture-code-compiler-plugin 本体) を
// `includeBuild` 経由で取り込み、 `dependencySubstitution` で coordinate を
// 解決する。
//
// root への相対パス計算:
//   jvm-sample (現在地)
//   .. = fixtures
//   ../.. = resources
//   ../../.. = test
//   ../../../.. = src
//   ../../../../.. = test-gradle-plugin
//   ../../../../../.. = integration-test
//   ../../../../../../.. = root
//
// ## 構成意図 (KMP publication と includeBuild の罠を回避するためのポイント)
//
// `pluginManagement { includeBuild(...) }` は **plugin 解決用** の include で、
// それ単独だと **通常 dependency への substitution は適用されない** (settings の
// pluginManagement scope と build-level の dependency scope は別の domain)。
//
// 一方、 top-level の `includeBuild(...) { dependencySubstitution { ... } }` は
// **build-level の dependency に対する substitution rule を設定** できる。
// 同じ build を 2 回 include しても 2 つ目は Gradle が dedup するが、
// dedup される際に substitution rule は **merge** される (Gradle 8+ の挙動)。
//
// この 2 重 include 構成によって:
//   - `plugins { id("me.tbsten.capture.code") version "..." }` の解決が
//     gradlePluginPortal でなく includeBuild 内の `:gradle-plugin` を使う
//   - `implementation("me.tbsten.capture.code:annotation:0.1.0-SNAPSHOT")` が
//     `:annotation` project に substitution される
// の両立が可能。
// ----------------------------------------------------------------------------
pluginManagement {
    includeBuild("../../../../../../..")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // fixture project の Kotlin 版を root build の Kotlin version と同期させる。
    // root の `libs.versions.toml` の Kotlin version を test 実行時に system property
    // で受け取り、 ここで plugin version を resolve する。 これにより root の
    // Kotlin version が変わっても fixture が automatic に追随する。
    plugins {
        val kotlinVersion: String =
            System.getProperty("test-gradle-plugin.kotlinVersion")
                ?: providers.gradleProperty("test-gradle-plugin.kotlinVersion").orNull
                ?: "2.0.21"
        kotlin("jvm") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

includeBuild("../../../../../../..") {
    dependencySubstitution {
        // 本体 :annotation の coordinate を root publication / platform-specific
        // publication 双方について明示的に substitute する。
        // KMP の場合、 implementation("me.tbsten.capture.code:annotation:...") が
        // jvmRuntimeClasspath では `-jvm` artifact (variant) を要求するため、
        // 両方を本 project に向ける。
        substitute(module("me.tbsten.capture.code:annotation"))
            .using(project(":annotation"))
        substitute(module("me.tbsten.capture.code:annotation-jvm"))
            .using(project(":annotation"))
    }
}

rootProject.name = "jvm-sample"
