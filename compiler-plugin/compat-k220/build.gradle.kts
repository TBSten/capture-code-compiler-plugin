// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for K2.2)。
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
        // root KGP (Kotlin 2.0.0) で kotlin-compiler-embeddable-2.2.0 を読むと、
        // 2.2.0 metadata format (binary version: 2.2.0) が 2.0.0 expected を超えるため
        // metadata version check で fail する。 本 module は **bundled-only**
        // (= ServiceLoader 経由でしか参照されない) であり、 main module 経由で
        // 2.2.0 シンボルを直接 import することは無いため、 metadata check を
        // skip しても安全。
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // Kotlin 2.2.x native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k220)
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

    // task-122: 本 module の `K220Diagnostics` 内 renderer は main module の English-only
    // SSoT (`MarkerAnnotationErrors` / `CapturedSourcesCallErrors`) を `<clinit>` で参照する。
    // test runtime で diagnostic factory を trigger する scenario では main module の class が
    // runtime classpath に必要。 main の compileKotlin output を file dependency として
    // testRuntimeOnly に投入する (詳細は compat-k200 の build.gradle.kts コメント参照)。
    testRuntimeOnly(
        project(
            mapOf("path" to ":compiler-plugin", "configuration" to "mainRuntimeClassesOnly"),
        ),
    )

    // task-074: compat-k220 専用 unit test (ServiceLoader-based sanity)。
    // kctfork は意図的に使わず pure JVM test に留める。 こうすることで
    // kctfork transitive embeddable と consumer Kotlin (matrix bumped to 2.2.x+)
    // の drift 問題を本 module の test では完全回避できる。
    // testRuntime に kotlin-compiler-embeddable-k220 (= 2.2.0 固定) を入れて
    // K220CompatContextImpl が ServiceLoader 経由でロードされる際に
    // 必要な FIR / IR symbol を解決可能にする。
    testImplementation(libs.kotlin.compiler.embeddable.k220)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
