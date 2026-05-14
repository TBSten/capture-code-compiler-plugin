plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
    id("buildsrc.convention.publish")
}

// ----------------------------------------------------------------------------
// :gradle-plugin:test の役割 — ProjectBuilder ベースの sanity test
//
// 本 test task は **`ProjectBuilder` (高速)** で Gradle plugin の DSL 配線を verify する
// sanity test レイヤ。 全シナリオを ProjectBuilder 内に閉じ込めて instant に走るので、
// PR 毎に毎回回す。
//
// ## 検証スコープ (ここでカバー)
//   - `CaptureCodeGradlePlugin.apply` で `captureCode { ... }` extension が登録される
//   - `afterEvaluate` で JVM project には `implementation` に、 KMP project には
//     `commonMainImplementation` に `:annotation` runtime 依存が追加される
//   - `applyToCompilation` が DSL の値を `SubpluginOption` (5 個) に変換する
//   - `getPluginArtifact` / `getCompilerPluginId` が期待値を返す
//
// ## ユーザ実利用形態の真の E2E (別レイヤ)
//
// 「`plugins { id("me.tbsten.capture.code") }` を実 Gradle build で apply して
// compiler plugin が attach され、 capturedSources の rewrite が実際に走る」 までを
// verify する真の E2E は **`:integration-test:test-gradle-plugin:test`** (task-040 で追加)
// に分離。 そちらは `Gradle TestKit + fixture project` を起動するため遅いが、
// includeBuild + dependencySubstitution の罠を踏まずに実利用形態を検証できる。
//
// ## scope 外 (test-kmp jvmTest が責任を持つ)
//   - 実 KMP build での compiler plugin 適用結果 (#101〜#105 の Source 値検証)
//   - 全 target (jvm/js/wasmJs/linuxX64/mingwX64) での IR transformer 動作
//
// ## 経緯
// task-026 で当初 TestKit + KMP fixture を試したが、 `includeBuild` の
// `dependencySubstitution` 周りで罠を踏み 5 回 retry も解決せず、 一旦本レイヤ
// (ProjectBuilder) で縮退した。 task-040 で **`project.group` を統一する**
// (root build.gradle.kts の `allprojects { group = ... }`) ことで substitution が
// 期待通りに動作することを発見し、 真の E2E は `:integration-test:test-gradle-plugin`
// で実現した。 詳細は `.local/ticket/done/task-040-*` を参照。
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
// task-037: Maven Central publish 設定
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
// 本 ticket スコープ外 (task-038 or 別 ticket)。 Maven Central に publish した
// plugin marker でも `plugins {}` syntax は動く (settings.gradle.kts の
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
