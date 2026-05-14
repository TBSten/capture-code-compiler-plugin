// ----------------------------------------------------------------------------
// task-037: この module は **publish しない** (internal)。
//
// `:compiler-plugin` の shadowJar に `bundled` configuration 経由で同梱され、
// 1 つの artifact (`me.tbsten.capture.code:compiler-plugin`) として配布される。
// そのため `mavenPublishing` block は **意図的に追加しない**。 個別 publish
// すると consumer 側で版違いを引きやすく、 shadowJar 同梱の SSOT を破壊する。
// ----------------------------------------------------------------------------
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
