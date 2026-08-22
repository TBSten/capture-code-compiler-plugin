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
// 以下では `warnOnEmptyCapture = true` を渡し、 `WarnNoMarkerFoundSample` (`src/test/kotlin/.../warning/`)
// で `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warning が compile log に乗ることを実機 verify する
// (task-120-B Phase 7)。 既存 marker は全て declaration / file annotation が紐づくため、 この
// option を有効にしても他 sample に副作用は出ない。 `--info` を付けて compileTestKotlin を走らせると
// warning が log に乗る:
//
//     ./gradlew --info :integration-test:test-jvm:compileTestKotlin | grep CC_CAPTUREDSOURCES_NO_MARKER_FOUND
//
// runtime 動作 (= empty list を返す) は sample の StringSpec assertion で確認する。
// ----------------------------------------------------------------------------
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-P", "plugin:me.tbsten.capture.code:warnOnEmptyCapture=true",
    )
}

// ----------------------------------------------------------------------------
// bug-001: incremental compilation の無効化
//
// 本 module は `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` で
// compiler plugin を直接 attach しており、 `:gradle-plugin` の IC 無効化
// (CaptureCodeGradlePlugin.disableIncrementalCompilation) を通らない。 IC round で
// marker / site file が compile 対象から外れると capture が壊れる (今は
// CC_*_MARKER_NOT_REGISTERED の compile error で検知される) ため、 module 側で
// incremental compilation を無効化する。 詳細: docs/known-limitations.md §6。
// ----------------------------------------------------------------------------
tasks.withType(org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile::class.java).configureEach {
    incremental = false
}
