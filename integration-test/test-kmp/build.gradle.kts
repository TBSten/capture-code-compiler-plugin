import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
}

// ----------------------------------------------------------------------------
// Apple native target の opt-in/opt-out
//
// Xcode 未インストール環境 (CommandLineTools のみ等) では ios* / macos* / tvos* /
// watchOS* といった Apple native target のリンクが失敗するため、Gradle property
// `enableAppleTargets` で切り替えられるようにする。
//
//   - `-PenableAppleTargets=true`  : ios* / macos* を有効化 (Xcode 必須)
//   - `-PenableAppleTargets=false` : 無効化 (デフォルト、ローカル CI 互換)
//
// `gradle.properties` (root or 個別 project) で `enableAppleTargets=true` を
// 設定すれば常時有効化することも可能。デフォルト false にしているのは、本
// リポジトリの dev 環境 (CommandLineTools 前提) でも `./gradlew assemble` が
// 通る状態を維持するため。
// ----------------------------------------------------------------------------
val enableAppleTargets: Boolean =
    (project.findProperty("enableAppleTargets") as? String)?.toBoolean() ?: false

@OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)
kotlin {
    // KMP 公式の階層テンプレートを有効化。
    // jvm/js/native の標準 source set 関係 (nativeMain → linuxX64Main 等) を提供する。
    // intermediate source set (jvmAndroidMain 等) を追加する際の土台でもある。
    applyDefaultHierarchyTemplate()

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) {
        nodejs()
    }

    // wasm 代表 target。 `:annotation` 側にも wasmJs target を持つため、
    // commonMain dependency (`:annotation`) は wasmJs 上でも解決される。
    // `browser()` は kotlin-browser-api の依存が必要となり setup コストが
    // 大きいので、当面 `nodejs()` のみで testing する。
    wasmJs {
        nodejs()
    }

    // Linux / Windows native (Xcode 不要)
    linuxX64()
    mingwX64()

    // Apple native (Xcode 必須 — opt-in)
    if (enableAppleTargets) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
        macosArm64()
        macosX64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":annotation"))
        }

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
        }

        // ----------------------------------------------------------------
        // intermediate **test** source set `jvmLinuxTest`.
        // ケース #105 (source set hierarchy) の挙動を test sourceset 階層で
        // 検証する。jvmAndroidTest (jvm + android の親) を本プロジェクトには
        // android target が無いため `jvmLinuxTest` (jvmTest + linuxX64Test の
        // 親) で代替する。
        //
        //   commonTest → jvmLinuxTest → { jvmTest, linuxX64Test }
        //   commonTest → { jsTest, wasmJsTest, mingwX64Test, appleTest* }
        //
        // marker / use site / capturedSources の全要素を test sourceset 内に
        // 置くため (main 系には KMP 検証用 fixture を一切置かない)、
        // `jvmLinuxMain` ではなく `jvmLinuxTest` を作る。
        // ----------------------------------------------------------------
        val jvmLinuxTest by creating {
            dependsOn(commonTest.get())
        }
        jvmTest {
            dependsOn(jvmLinuxTest)
        }
        named("linuxX64Test") {
            dependsOn(jvmLinuxTest)
        }
    }
}

dependencies {
    // KMP 全 target の compilation に compiler plugin を attach する。
    // `kotlinCompilerPluginClasspath` configuration は KGP が自動で各 target の
    // KotlinCompile task に伝播するため、ここで指定するだけで jvm / js /
    // wasm / native すべての compilation に plugin が乗る。
    //
    // 将来的に `:gradle-plugin` 経由 (`plugins { id("me.tbsten.capture.code") }`)
    // への移行は TestKit による DSL 検証で別途扱う。
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
