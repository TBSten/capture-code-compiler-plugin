import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("buildsrc.convention.publish")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)

    jvm()
    js { browser(); nodejs() }
    wasmJs { browser(); nodejs() }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Publishing to Maven Central
// https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
//
// 共通設定 (publishToMavenCentral / pom の license / developer / scm / signing 条件)
// は `buildsrc.convention.publish` convention plugin が提供。 ここでは KMP 固有の
// coordinates と module-specific な name / description のみ override する。
// coordinates は vanniktech が `project.group:project.name:project.version` を
// 既定で使うため、 root の `allprojects { group = GROUP; version = VERSION_NAME }`
// と settings.gradle.kts の `include(":annotation")` で決まる
// `me.tbsten.capture.code:annotation:<VERSION_NAME>` がそのまま使われる。
// SSOT を破壊しないため `coordinates(...)` の明示呼び出しはしない。
mavenPublishing {
    pom {
        name.set("Capture Code annotation")
        description.set("Runtime API declarations for Capture Code compiler plugin")
    }
}
