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
// task-018 (Gradle plugin DSL options): sample for task-015 / task-026 follow-up
//
// `:gradle-plugin` の `CaptureCodeExtension` を経由する場合は build.gradle.kts に:
//
//     plugins { id("me.tbsten.capture.code") }
//     captureCode { dedent = false }
//
// と書く。現在の integration-test は `kotlinCompilerPluginClasspath(project(":compiler-plugin"))`
// で plugin を直接 attach しているため、Gradle DSL ではなく CLI option を直接渡す形になる。
// 将来 task-015 (実消費) と task-026 (TestKit 経由の DSL 検証) が揃ったら、シナリオ別 sub-project を
// 切るか、以下のような freeCompilerArgs で plugin option を渡す方式を採用する:
//
//     tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//         compilerOptions.freeCompilerArgs.addAll(
//             "-P", "plugin:me.tbsten.capture.code:dedent=false",
//         )
//     }
//
// 本 ticket では配線のみ固める方針なので、上記は **コメントとして** 残す。
// ----------------------------------------------------------------------------
