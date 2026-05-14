plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

// ----------------------------------------------------------------------------
// task-026: Gradle plugin の sanity test (ProjectBuilder ベース)。
//
// **設計判断 (縮退方針)**:
// 当初は Gradle TestKit + fixture KMP project (`src/functionalTest/resources/`) で
// `:gradle-plugin` 経由の plugin 適用を E2E 検証する方針だったが、 以下の理由で
// **ProjectBuilder ベースの unit test** に縮退した:
//
//   1. fixture project は `includeBuild` で本 build を参照する構成だが、
//      KMP の root publication (`kotlinMultiplatform` variant) と
//      `available-at` 経由の platform-specific module (annotation-jvm 等) の
//      組み合わせで `dependencySubstitution` が期待通りに効かず、 fixture の
//      `jvmTestRuntimeClasspath` 解決が `me.tbsten.capture.code:annotation:0.1.0-SNAPSHOT`
//      を MavenCentral に探しに行って失敗する (task-026 5 回試行で再現)。
//   2. `:annotation` を `publishToMavenLocal` で先回りする回避策は可能だが、
//      `:compiler-plugin` 側に maven-publish 設定が無く、 全モジュール publish の
//      準備が必要 (task-037 の領域 — Phase 5)。
//   3. **より重要**: `#101-#105 の挙動検証** は既存の `:integration-test:test-kmp:jvmTest`
//      (target 5 + intermediate test sourceset + 5 ケース PASS) が「integration test の
//      本体」として既に存在する。 TestKit fixture で再検証してもカバレッジは増えない。
//      Gradle plugin 側で verify したいのは **「plugin の DSL 配線 (extension /
//      dependency 自動追加 / SubpluginOption 変換)」** のみで、 これは
//      `ProjectBuilder` で十分検証可能。
//
// 本 ticket の検証スコープ:
//   - `CaptureCodeGradlePlugin.apply` で `captureCode { ... }` extension が登録される
//   - `afterEvaluate` で JVM project には `implementation` に、 KMP project には
//     `commonMainImplementation` に `:annotation` runtime 依存が追加される
//   - `applyToCompilation` が DSL の値を `SubpluginOption` (5 個) に変換する
//   - `getPluginArtifact` / `getCompilerPluginId` が期待値を返す
//
// scope 外 (jvmTest が責任を持つ):
//   - 実 KMP build での compiler plugin 適用結果 (#101〜#105 の Source 値検証)
//   - 全 target (jvm/js/wasmJs/linuxX64/mingwX64) での IR transformer 動作
//
// 上記 scope 外の検証は `:integration-test:test-kmp:jvmTest` で完結する。 詳細は
// `.local/ticket/task-026-kmp-integration-test.md` 末尾の完了メモ参照。
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
