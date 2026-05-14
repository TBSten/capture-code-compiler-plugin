rootProject.name = "capture-code-compiler-plugin"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}

include(":annotation")
include(":compiler-plugin")
include(":compiler-plugin:compat")
include(":compiler-plugin:compat-k200")
include(":compiler-plugin:compat-k210")
include(":gradle-plugin")
include(":integration-test:test-jvm")
include(":integration-test:test-kmp")
include(":integration-test:test-gradle-plugin")

// ----------------------------------------------------------------------------
// task-035: エンドユーザ向け sample module 群 (実行可能 cookbook)
//
// samples は root build の subproject として配置する。 publish 前 (現状) では
// `plugins { id("me.tbsten.capture.code") version "X.Y" }` は plugin marker
// artifact が repository に published されている前提のため使えない。 そこで
// integration-test/test-jvm と同様、 `kotlinCompilerPluginClasspath(project(
// ":compiler-plugin"))` で直接 compiler plugin を attach し、 DSL option は
// `freeCompilerArgs` で渡す形式を採用する。
//
// 真の E2E (`plugins { id(...) }` 経由の attach) は task-040 で
// `:integration-test:test-gradle-plugin` (TestKit + includeBuild fixture) が
// カバー済み。 samples の責務は 「コード使用例 (cookbook) を見せる」 ことであり、
// publish 後にユーザがどう書くかは README / build.gradle.kts コメントで案内する。
// ----------------------------------------------------------------------------
include(":samples:jvm-sample")
include(":samples:kmp-sample")
