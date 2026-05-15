// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for Kotlin 2.4.0-RC)。
// `:compiler-plugin` の shadowJar に bundled で同梱され、 META-INF/services の
// CompilerCompat factory 経由でロードされる。 詳細は `:compiler-plugin/compat/
// build.gradle.kts` の comment 参照。
// ----------------------------------------------------------------------------
plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        // root KGP (Kotlin 2.0.0) で kotlin-compiler-embeddable-2.4.0-RC を読むと、
        // 2.4.0-RC metadata format (binary version: 2.4.0) が 2.0.0 expected を超えるため
        // metadata version check で fail する。 本 module は **bundled-only**
        // (= ServiceLoader 経由でしか参照されない) であり、 main module 経由で
        // 2.4.0-RC シンボルを直接 import することは無いため、 metadata check を
        // skip しても安全。
        freeCompilerArgs.add("-Xskip-metadata-version-check")
        // task-075 から継承: Kotlin 2.3.0 で diagnostic factory の `error0` / `error1`
        // 等が `context(KtDiagnosticsContainer)` / 受け手付きの contextual declaration
        // に変わって以降、 2.4.0-RC でも同じ shape のため、 root KGP (Kotlin 2.0.0)
        // compile path で本 module を build するため、 context-receivers 機能の
        // opt-in flag を有効化する。
        //
        // task-077: Kotlin 2.3+ では `-Xcontext-receivers` が削除済 (error) に
        // なっているため、 root Kotlin が 2.3+ のときは `-Xcontext-parameters` を使う。
        val rootKotlinVersion = org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION
        val (major, minor) = rootKotlinVersion.split('.').let {
            (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        if (major > 2 || (major == 2 && minor >= 3)) {
            freeCompilerArgs.add("-Xcontext-parameters")
        } else {
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }
}

dependencies {
    // Kotlin 2.4.0-RC native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k240rc)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))

    // task-076: compat-k240rc 専用 unit test (ServiceLoader-based sanity)。
    // kctfork は意図的に使わず pure JVM test に留める。 こうすることで
    // kctfork transitive embeddable と consumer Kotlin (matrix bumped to 2.4.0-RC+)
    // の drift 問題を本 module の test では完全回避できる。
    // testRuntime に kotlin-compiler-embeddable-k240rc (= 2.4.0-RC 固定) を入れて
    // K240RcCompatContextImpl が ServiceLoader 経由でロードされる際に
    // 必要な FIR / IR symbol を解決可能にする。
    testImplementation(libs.kotlin.compiler.embeddable.k240rc)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
