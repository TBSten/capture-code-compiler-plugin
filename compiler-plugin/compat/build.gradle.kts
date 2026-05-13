plugins {
    id("buildsrc.convention.kotlin-jvm")
}

kotlin {
    compilerOptions {
        // 全 compat-kXXXX が依存できるように、最小サポート Kotlin (2.0) で API を保つ
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
}
