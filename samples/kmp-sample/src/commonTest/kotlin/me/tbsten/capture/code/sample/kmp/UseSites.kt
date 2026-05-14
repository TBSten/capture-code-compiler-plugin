package me.tbsten.capture.code.sample.kmp

// ============================================================================
// KMP sample: marker の use site (commonTest 配置)
//
// commonTest に置いた use site は jvm / js / linuxX64 / mingwX64 の全 test
// compilation で可視。 本 sample では jvm target からのみ test 実行するが、
// 他 target で `:linkDebugTest<Target>` 系で compile error が出なければ
// 全 target で plugin が動いている証拠となる。
// ============================================================================

// --- simple snippet (Source filler のみ) ---
@KmpSnippet
internal fun kmpSampleAdd(a: Int, b: Int): Int = a + b

@KmpSnippet
internal fun kmpSampleMul(a: Int, b: Int): Int = a * b

// --- detailed snippet (Source + SourceLocation + CaptureKind) ---
@KmpDetailed
internal fun kmpSampleHello(): String = "hello from common"

// --- feature flag-like marker with user-defined name parameter ---
@KmpFeature(name = "dark-mode")
internal fun featureDarkMode(): Boolean = true

@KmpFeature(name = "experimental-search")
internal fun featureExperimentalSearch(): Boolean = false
