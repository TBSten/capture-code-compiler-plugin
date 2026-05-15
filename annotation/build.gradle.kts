plugins {
    kotlin("multiplatform")
    // task-085: AGP は buildSrc/build.gradle.kts で classpath に乗っているため
    // version 指定は省略 (バージョン重複指定で `Error resolving plugin` になる)。
    // version SSOT は libs.versions.toml の `agp = "8.5.2"`。
    id("com.android.library")
    id("buildsrc.convention.publish")
}

// Note: `ExperimentalWasmDsl` import + `@OptIn` は Kotlin 2.2.x まで必要だったが、
// Kotlin 2.3 で削除予定の API になっており 2.3.x compile を壊す。 wasmJs は 2.3 以降
// stable 扱いで OptIn 不要。 2.2 系では `@OptIn` 無しでも warning のみで build は通る
// (= experimental API への参照は無い: `kotlin { wasmJs { ... } }` のみで OptIn 不要)。

// ----------------------------------------------------------------------------
// Apple native target の opt-in/opt-out (`:integration-test:test-kmp` と同方式)
//
// Xcode 未インストール環境 (CommandLineTools のみ等) では ios* / macos* の link
// が `xcrun exit code 72` で失敗するため、 Gradle property `enableAppleTargets`
// で切り替え可能にする。
//
//   - `-PenableAppleTargets=true`  : ios* / macos* を有効化 (Xcode 必須)
//   - `-PenableAppleTargets=false` : 無効化 (デフォルト、 ローカル dev 互換)
//
// Maven Central publish 時は `release.yml` が macos-latest runner で
// `-PenableAppleTargets=true` を付けるため、 Apple native artifact は全て
// publish される。
// ----------------------------------------------------------------------------
val enableAppleTargets: Boolean =
    (project.findProperty("enableAppleTargets") as? String)?.toBoolean() ?: false

kotlin {
    jvmToolchain(17)

    jvm()
    androidTarget {
        // release variant のみ Maven Central に publish する。 debug variant は
        // テスト/プレビュー用なので artifact には含めない (一般的な KMP library パターン)。
        publishLibraryVariants("release")
    }
    js { browser(); nodejs() }
    wasmJs { browser(); nodejs() }
    linuxX64()
    mingwX64()

    if (enableAppleTargets) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
        macosArm64()
        macosX64()
    }

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ----------------------------------------------------------------------------
// AGP 設定 (Android target を持つ KMP library として必須)
//
// `:annotation` は runtime API 宣言のみで Android 固有 code は無いが、
// `androidTarget()` を有効化した KMP project には AGP `android { ... }` block が
// 必須となる (namespace / compileSdk / minSdk)。
//
// minSdk = 21: Android 5.0+ (約 99% のデバイスをカバー)。 LeakCanary 等の
// 主要 KMP library と同じデフォルト。
// compileSdk = 35: AGP 8.5.2 がサポートする最新 stable Android SDK。
// ----------------------------------------------------------------------------
android {
    namespace = "me.tbsten.capture.code.annotation"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
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
