// ----------------------------------------------------------------------------
// KMP fixture build script
//
// `plugins { id("me.tbsten.capture.code") }` でユーザ実利用形態の plugin apply を
// 行い、 KMP project に対しても compiler plugin が正しく attach されることを
// TestKit から verify する。
//
// scope:
//   - jvm() target のみ (多 target は :integration-test:test-kmp で別途検証)
//   - commonTest に marker / use site を配置
//   - jvmTest で kotest StringSpec から `capturedSources<T>()` の結果を assert
// ----------------------------------------------------------------------------
plugins {
    // Kotlin Multiplatform version は settings.gradle.kts の pluginManagement で
    // declare 済 (root の libs.versions.toml の Kotlin version と同期される)。
    kotlin("multiplatform")
    id("me.tbsten.capture.code")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("io.kotest:kotest-runner-junit5:5.9.1")
            implementation("io.kotest:kotest-assertions-core:5.9.1")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// `captureCode` extension がちゃんと露出することの sanity check として、 DSL ブロック
// を書いてみる。 値はデフォルトと同値だが、 plugin apply 順番ミスや extension 未登録
// だと build script がコンパイルエラーになる。
captureCode {
    dedent = true
    includeKdoc = true
}
