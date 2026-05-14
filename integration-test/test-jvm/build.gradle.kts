plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":annotation"))
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

// ----------------------------------------------------------------------------
// Gradle plugin DSL options sample
//
// `:gradle-plugin` の `CaptureCodeExtension` を経由する場合は build.gradle.kts に:
//
//     plugins { id("me.tbsten.capture.code") }
//     captureCode { dedent = false }
//
// と書く。現在の integration-test は `kotlinCompilerPluginClasspath(project(":compiler-plugin"))`
// で plugin を直接 attach しているため、Gradle DSL ではなく CLI option を直接渡す形になる。
// 将来、シナリオ別 sub-project を切るか、以下のような freeCompilerArgs で plugin option を渡す方式を
// 採用することも可能:
//
//     tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//         compilerOptions.freeCompilerArgs.addAll(
//             "-P", "plugin:me.tbsten.capture.code:dedent=false",
//         )
//     }
//
// 本モジュールでは配線のみ固める方針のため、上記は **コメントとして** 残す。
// ----------------------------------------------------------------------------
