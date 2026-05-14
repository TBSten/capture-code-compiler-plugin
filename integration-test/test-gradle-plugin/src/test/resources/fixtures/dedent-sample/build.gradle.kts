// ----------------------------------------------------------------------------
// dedent DSL option の E2E fixture (baseline = dedent=true)。
//
// `DslOptionsE2eTest` がこの build.gradle.kts を tmp dir コピー時に書き換えて
// `captureCode { dedent = true }` / `captureCode { dedent = false }` の 2 ケース
// を評価する。 baseline は `dedent = true` (= デフォルトと同値の明示指定)。
// 上書きするマーカーとして `// DEDENT_PLACEHOLDER` 行を残しておく。
// ----------------------------------------------------------------------------
plugins {
    kotlin("jvm")
    id("me.tbsten.capture.code")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "me.tbsten.capture.code.sample.MainKt"
}

captureCode {
    // DEDENT_PLACEHOLDER
    dedent = true
}
