import java.time.Duration

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

// ----------------------------------------------------------------------------
// Gradle TestKit + fixture project による真の E2E テストモジュール
//
// fixture project の `settings.gradle.kts` で **明示的に `dependencySubstitution`**
// を書くことで、 Maven Central への publish を待たずに本リポジトリの未公開ビルド
// 成果物を `me.tbsten.capture.code:...` 座標へ差し替え、 ユーザ実利用形態を E2E で
// 検証する。
//
// 役割分担 (Gradle plugin の test レイヤ):
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
        // `kotlin-k210` 等の派生 library エントリにより `libs.versions.kotlin` accessor が
        // namespace 化されているため `.asProvider().get()` で明示。
        libs.versions.kotlin.asProvider().get(),
    )

    // includeBuild + dependencySubstitution は configuration-cache と相性問題が出る場合があるため、
    // fixture 側で `--no-configuration-cache` を渡す方針 (test code で明示)。

    // TestKit 経由の Gradle build は遅い (1 ケース 30-90 秒程度)。 timeout を伸ばす。
    timeout.set(Duration.ofMinutes(15L))
}
