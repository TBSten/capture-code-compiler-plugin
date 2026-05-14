// ----------------------------------------------------------------------------
// This module is **not published** (internal compat layer).
// It is bundled into the `:compiler-plugin` shadowJar and loaded via the
// META-INF/services CompilerCompat factory. See `:compiler-plugin/compat/
// build.gradle.kts` for details.
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
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))
}
