import java.time.Duration

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

// ----------------------------------------------------------------------------
// task-040: Gradle TestKit + fixture project による真の E2E テスト
//
// task-026 では `includeBuild` 経由で fixture project を取り込んだ際に、
// KMP root publication と `available-at` 経由の platform-specific module
// (`annotation-jvm` 等) の組み合わせで `dependencySubstitution` が完全には
// 効かず failed したため、 ProjectBuilder ベースの sanity test に縮退した。
//
// 本モジュールは task-040 でその縮退分を取り戻すための再着手。 fixture project の
// `settings.gradle.kts` で **明示的に `dependencySubstitution`** を書くことで、
// `maven-publish` 配線 ([[task-037]]) を待たずに E2E 検証する。
//
// 役割分担:
//   - :gradle-plugin:test               = ProjectBuilder = DSL 配線の高速 sanity
//   - :integration-test:test-gradle-plugin:test
//                                       = TestKit fixture = ユーザ実利用形態の真の E2E
//
// fixture は `src/test/resources/fixtures/<name>/` に配置。 個々の fixture は
// 独立した Gradle project であり、 root build には include されない。 test 実行時に
// `GradleRunner.withProjectDir(...)` で fixture project を起動する。
// ----------------------------------------------------------------------------

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    // TestKit が起動する子 Gradle build は本 build と同じ Gradle user home を共有する。
    // 親 build の test daemon 経由で fixture が走る場合、 fixture の Kotlin 解決等が
    // 親 build の キャッシュを利用できる利点がある。
    //
    // fixture から本リポジトリへの相対パス計算上、 fixture project の working dir
    // (= `src/test/resources/fixtures/<name>/`) を test JVM のカレントとして渡す必要は無い。
    // GradleRunner.withProjectDir(...) が project dir を JVM とは独立に処理するため。
    systemProperty("test-gradle-plugin.fixturesDir", file("src/test/resources/fixtures").absolutePath)

    // fixture の Kotlin plugin version を root の libs.versions.toml と同期する。
    // GradleRunner test 側で `-Ptest-gradle-plugin.kotlinVersion=<version>` を fixture に渡し、
    // fixture の settings.gradle.kts の pluginManagement で resolve する。
    systemProperty(
        "test-gradle-plugin.kotlinVersion",
        // task-030 v2: `kotlin-k210` library エントリ追加で `libs.versions.kotlin` accessor が
        // namespace 化されたため `.asProvider().get()` で明示。
        libs.versions.kotlin.asProvider().get(),
    )

    // includeBuild + dependencySubstitution は configuration-cache と相性問題が出る場合があるため、
    // fixture 側で `--no-configuration-cache` を渡す方針 (test code で明示)。

    // TestKit 経由の Gradle build は遅い (1 ケース 30-90 秒程度)。 timeout を伸ばす。
    timeout.set(Duration.ofMinutes(15L))
}
