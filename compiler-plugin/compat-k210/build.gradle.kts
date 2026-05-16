// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for K2.1)。
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
    }
}

dependencies {
    // Kotlin 2.1.x native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k210)
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

    // task-065: compat-k210 専用 unit test (ServiceLoader-based sanity)。
    // kctfork は意図的に使わず pure JVM test に留める。 こうすることで
    // kctfork transitive embeddable と consumer Kotlin (matrix bumped to 2.1.x+)
    // の drift 問題を本 module の test では完全回避できる。
    // testRuntime に kotlin-compiler-embeddable-k210 (= 2.1.0 固定) を入れて
    // K210CompatContextImpl が ServiceLoader 経由でロードされる際に
    // 必要な FIR / IR symbol を解決可能にする。
    testImplementation(libs.kotlin.compiler.embeddable.k210)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
