// ----------------------------------------------------------------------------
// task-040 follow-up (post-completion polish):
//   `:integration-test:test-jvm:DslOptionsTest` の 2 ケース
//   (「dedent=true (デフォルト)」 / 「dedent=false ではインデントが保持される」)
//   を Gradle TestKit + fixture project で再構築するための fixture。
//
// 役割:
//   - `plugins { id("me.tbsten.capture.code") }` でユーザ実利用形態 (= Gradle DSL
//     `captureCode { dedent = ... }` を経由) で plugin を attach
//   - test 実行時に build.gradle.kts を tmp dir コピー時に書き換えて、 dedent=true
//     と dedent=false の 2 シナリオを 1 つの fixture で評価
//
// 構造は `jvm-sample` と同じパターン (`includeBuild("../../../../../../..")` で
// root プロジェクトを 2 重 include して plugin と annotation を解決) を踏襲する。
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
        substitute(module("me.tbsten.capture.code:annotation"))
            .using(project(":annotation"))
        substitute(module("me.tbsten.capture.code:annotation-jvm"))
            .using(project(":annotation"))
    }
}

rootProject.name = "dedent-sample"
