// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for K2.3)。
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
        // root KGP (Kotlin 2.0.0) で kotlin-compiler-embeddable-2.3.0 を読むと、
        // 2.3.0 metadata format (binary version: 2.3.0) が 2.0.0 expected を超えるため
        // metadata version check で fail する。 本 module は **bundled-only**
        // (= ServiceLoader 経由でしか参照されない) であり、 main module 経由で
        // 2.3.0 シンボルを直接 import することは無いため、 metadata check を
        // skip しても安全。
        freeCompilerArgs.add("-Xskip-metadata-version-check")
        // task-075: Kotlin 2.3.0 で diagnostic factory の `error0` / `error1` 等が
        // `context(KtDiagnosticsContainer)` / 受け手付きの contextual declaration に
        // 変わっている。 root KGP (Kotlin 2.0.0) compile path で本 module を build
        // するため、 context-receivers 機能の opt-in flag を有効化する。
        //
        // task-077: Kotlin 2.3+ では `-Xcontext-receivers` が削除済 (error) に
        // なっているため、 root Kotlin が 2.3+ のときは代わりに `-Xcontext-parameters`
        // を使う。 root の Kotlin バージョンに応じて切り替える。
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
    // Kotlin 2.3.x native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k230)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))

    // task-118: main module の feature/markerDefinition / feature/capturedSources 配下に置かれた
    // domain SSOT を参照する。 単純な `compileOnly(project(":compiler-plugin"))` は循環依存に
    // なる (main の apiElements が shadowJar を返し、 shadowJar が compat-k* の jar を bundled
    // で要求するため) ので、 shadowJar を bypass する `mainClassesOnly` outgoing configuration
    // (compileKotlin output directory のみを expose) を引く。 compat-k200 の build.gradle.kts
    // のコメントも参照。
    compileOnly(
        project(
            mapOf("path" to ":compiler-plugin", "configuration" to "mainClassesOnly"),
        ),
    )

    // task-075: compat-k230 専用 unit test (ServiceLoader sanity + task-088 で追加した
    // static init smoke test)。 kctfork は意図的に使わず pure JVM test に留める
    // (kctfork 0.5.1 は Kotlin 2.0.x baseline で固定されており、 K230 native API には
    // NoSuchMethodError で fail する)。 task-088 の `K230CaptureCodeDiagnosticsStaticInitTest`
    // は kctfork 不要で **直接 diagnostic property を read** することで static init の
    // 循環依存を検出する設計。
    testImplementation(libs.kotlin.compiler.embeddable.k230)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
