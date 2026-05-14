// ----------------------------------------------------------------------------
// task-040: JVM-only fixture build script
//
// `plugins { id("me.tbsten.capture.code") }` でユーザ実利用形態の plugin apply を
// 行う。 plugin の `apply()` 内で:
//   - `captureCode { ... }` extension が registration される
//   - `afterEvaluate` で `:annotation` runtime 依存が `implementation` に自動追加される
// が走ることを GradleRunner 経由で verify する。
// ----------------------------------------------------------------------------
plugins {
    // Kotlin version は settings.gradle.kts の `pluginManagement { plugins { } }` 内で
    // declare 済 (root の libs.versions.toml の Kotlin version と同期される)。
    kotlin("jvm")
    id("me.tbsten.capture.code")
    application
}

// repositories は settings.gradle.kts の `dependencyResolutionManagement` で
// 一元管理する (project-level の repositories を書くと substitution rule が
// 効きにくくなる場合がある)。

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "me.tbsten.capture.code.sample.MainKt"
}

// `captureCode` extension がちゃんと露出することの sanity check として、 DSL ブロックを
// 書いてみる。 値はデフォルトと同じだが、 plugin apply 順番ミスや extension 未登録だと
// build script がコンパイルエラーになる。
captureCode {
    dedent = true
    includeKdoc = true
}
