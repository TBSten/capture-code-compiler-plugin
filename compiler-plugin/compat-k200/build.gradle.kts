// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for K2.0)。
// `:compiler-plugin` の shadowJar に bundled で同梱され、 META-INF/services の
// CompilerCompat factory 経由でロードされる。 詳細は `:compiler-plugin/compat/
// build.gradle.kts` の comment 参照。
//
// task-063: consumer (root の `kotlin`) が 2.0.20+ に bump されても、 この module
// は 2.0.0 native API (例: `FirLiteralExpression<*>`) で compile する必要が
// あるため、 `libs.kotlin.compiler.embeddable.k200` (kotlin-k200 固定 ref)
// を使う。 これで CI matrix の他バージョン bump から独立化できる。
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
    // Kotlin 2.0.x native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k200)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))

    // task-065: compat-k200 専用 unit test (ServiceLoader-based sanity)。
    // kctfork を使わず pure JVM test に留めるため、 kctfork transitive
    // (kotlin-compiler-embeddable 2.0.0) と consumer Kotlin の drift 問題を
    // この module の test では完全回避する。 K200CompatContextImpl 自体は
    // K2.0 native API で書かれているので testRuntime に
    // kotlin-compiler-embeddable-k200 (= 2.0.0 固定) を入れて
    // ServiceLoader 経由でロード可能にする。
    testImplementation(libs.kotlin.compiler.embeddable.k200)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
