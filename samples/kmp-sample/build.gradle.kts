import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

// ----------------------------------------------------------------------------
// task-035: Kotlin Multiplatform sample for the Capture Code compiler plugin.
//
// 本 module は **エンドユーザ向け cookbook (KMP 版)** であり、 jvm + js + native
// (linux / mingw) の各 target で plugin が動作することを実機で示す。
//
// ## test sourceset 完結方式
//
// KMP では design §13 Known Limitations に従い、 marker / use site / `captured
// Sources<T>()` 呼び出しを **すべて test sourceset** に置く必要がある (commonMain
// で marker を宣言し commonTest から呼ぶと、 IR transform が rewrite しない
// ケースがあるため)。 task-021 で確立した「test sourceset 完結方式」を踏襲する。
//
//   commonTest → jvmTest (kotest junit5 runner で実行)
//
// ## 外部プロジェクトに持っていくときの差し替え手順 (publish 後を想定)
//
//   plugins {
//       kotlin("multiplatform") version "2.0.0"
//       id("me.tbsten.capture.code") version "0.1.0-SNAPSHOT"
//   }
//
//   kotlin {
//       jvm(); js { nodejs() }
//       sourceSets {
//           commonMain.dependencies {
//               implementation("me.tbsten.capture.code:annotation:0.1.0-SNAPSHOT")
//           }
//       }
//   }
//
//   captureCode { dedent = true; ... }
//
// publish 前の現状では `kotlinCompilerPluginClasspath(project(":compiler-plugin"))`
// で直接 attach する (integration-test/test-kmp と同等)。
// ----------------------------------------------------------------------------
plugins {
    kotlin("multiplatform")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(17)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        nodejs()
        binaries.executable()
    }

    // Xcode-free native target (test-kmp と同じ範囲)。
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            // ↓ 外部 project では:
            //   implementation("me.tbsten.capture.code:annotation:<VERSION>")
            implementation(project(":annotation"))
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
        }
    }
}

dependencies {
    // 全 target の compilation に compiler plugin を attach。
    // KGP が `kotlinCompilerPluginClasspath` を各 target の KotlinCompile task に
    // 自動伝播するため、 ここで一度宣言するだけで jvm / js / native すべての
    // compilation に plugin が乗る (test-kmp と同等の方式)。
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
