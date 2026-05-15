plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
    id("buildsrc.convention.publish")
}

// ----------------------------------------------------------------------------
// Gradle plugin の test レイヤ構成 — ProjectBuilder (sanity) と TestKit (E2E) の役割分担
//
// 本モジュールの `:gradle-plugin:test` は **`ProjectBuilder` ベースの sanity test**。
// 全シナリオを ProjectBuilder 内に閉じ込めて instant に走るので、 PR 毎に毎回回す。
//
// ## ここでカバーする検証スコープ (sanity = DSL 配線レベル)
//   - `CaptureCodeGradlePlugin.apply` で `captureCode { ... }` extension が登録される
//   - `afterEvaluate` で JVM project には `implementation` に、 KMP project には
//     `commonMainImplementation` に `:annotation` runtime 依存が追加される
//   - `applyToCompilation` が DSL の値を `SubpluginOption` (5 個) に変換する
//   - `getPluginArtifact` / `getCompilerPluginId` が期待値を返す
//
// ## 真の E2E は別モジュール (`:integration-test:test-gradle-plugin:test`)
//
// 「`plugins { id("me.tbsten.capture.code") }` を実 Gradle build で apply して
// compiler plugin が attach され、 capturedSources の rewrite が実際に走る」 までを
// verify する真の E2E は **`:integration-test:test-gradle-plugin:test`** に分離。
// そちらは `Gradle TestKit + fixture project` を起動するため遅いが、 includeBuild +
// dependencySubstitution を踏み抜かずに実利用形態を検証できる。 fixture project は
// root の `allprojects { group = ... }` で `project.group` を統一することで
// dependencySubstitution が期待通り動作する構成にしている。
//
// ## scope 外 (test-kmp jvmTest が責任を持つ)
//   - 実 KMP build での compiler plugin 適用結果 (#101〜#105 の Source 値検証)
//   - 全 target (jvm/js/wasmJs/linuxX64/mingwX64) での IR transformer 動作
// ----------------------------------------------------------------------------

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(project(":compiler-plugin"))
    implementation(project(":annotation"))

    // ProjectBuilder + Kotest を使った plugin sanity test。
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

gradlePlugin {
    plugins {
        create("captureCode") {
            id = "me.tbsten.capture.code"
            displayName = "Capture Code Compiler Plugin"
            description = "Kotlin compiler plugin for capturing source code"
            implementationClass = "me.tbsten.capture.code.gradle.CaptureCodeGradlePlugin"
        }
    }
}

// ----------------------------------------------------------------------------
// Maven Central publish 設定
//
// `java-gradle-plugin` plugin は標準で 2 つの publication を自動生成する:
//   1. main publication (artifactId = project.name = "gradle-plugin")
//   2. plugin marker publication (artifactId = `<plugin-id>.gradle.plugin`、
//      = "me.tbsten.capture.code.gradle.plugin"、 これにより
//      `plugins { id("me.tbsten.capture.code") version "..." }` syntax が動く)
//
// vanniktech plugin は `java-gradle-plugin` を検出して `GradlePlugin` mode に
// 自動切替し、 上記 2 publication 両方に共通 POM を付与する。 ここでは
// coordinates の groupId / artifactId / version のみ override する。
//
// Gradle Plugin Portal への publish (`com.gradle.plugin-publish` plugin) は
// 本モジュールのスコープ外。 Maven Central に publish した plugin marker でも
// `plugins {}` syntax は動く (settings.gradle.kts の
// `pluginManagement { repositories { mavenCentral() } }` 経由)。
// ----------------------------------------------------------------------------
mavenPublishing {
    // coordinates は `project.group:project.name:project.version` (root 集約値) を
    // そのまま使用 (`:annotation` と同じ理由 — SSOT 維持)。 plugin marker 側の
    // artifactId (`me.tbsten.capture.code.gradle.plugin`) は java-gradle-plugin
    // が自動生成する。
    pom {
        name.set("Capture Code Gradle plugin")
        description.set("Gradle plugin wrapper for the Capture Code Kotlin compiler plugin")
    }
}

// ----------------------------------------------------------------------------
// VERSION 注入: gradle.properties の VERSION_NAME を build-time 生成の
// `PluginVersion.kt` に書き込み、 `CaptureCodeGradlePlugin.VERSION` の SSOT を
// gradle.properties に統一する。
//
// この生成された `VERSION` 値は `getPluginArtifact()` の
// `SubpluginArtifact.version` に渡され、 consumer の Kotlin compile task に
// `compiler-plugin:<VERSION>` を classpath として注入する用途で使われる。
// ハードコードのままだと publish 後に `compiler-plugin:0.1.0-SNAPSHOT` を
// resolve しようとして fail するため、 gradle.properties → 生成 const に
// 統一する必要がある (SSOT)。
// ----------------------------------------------------------------------------
val generatedVersionDir = layout.buildDirectory.dir("generated/source/pluginVersion/kotlin")

val generatePluginVersion = tasks.register("generatePluginVersion") {
    val outDir = generatedVersionDir
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("me/tbsten/capture/code/gradle")
        dir.mkdirs()
        dir.resolve("PluginVersion.kt").writeText(
            """
            |// 自動生成。 編集禁止 (build script の generatePluginVersion task で出力される)。
            |package me.tbsten.capture.code.gradle
            |
            |internal const val CAPTURE_CODE_PLUGIN_VERSION: String = "$versionValue"
            |
            """.trimMargin()
        )
    }
}

kotlin {
    sourceSets["main"].kotlin.srcDir(generatedVersionDir)
}

// `compileKotlin` だけでなく、 sources jar / dokka 等の生成 source を読む全 task
// に依存させる。 個別 dependsOn を書かずに matching で網羅する方が安全。
tasks.matching { it.name == "compileKotlin" || it.name == "sourcesJar" || it.name == "kotlinSourcesJar" || it.name == "dokkaHtml" || it.name == "dokkaJavadoc" }.configureEach {
    dependsOn(generatePluginVersion)
}
