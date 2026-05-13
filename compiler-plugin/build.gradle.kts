plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
    alias(libs.plugins.shadow)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

// shadowJar に同梱する compat 実装モジュール (transitive 依存は持ち込まない)
val bundled: Configuration by configurations.creating { isTransitive = false }

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))

    // 全 compat-kXXXX を shadow JAR で同梱する。バージョン追加時はここに足す。
    bundled(project(":compiler-plugin:compat-k2000"))

    testImplementation(project(":annotation"))
    // Unit test では shadow JAR ではなく素の classpath を使うので、
    // compat 実装モジュールも明示的に testImplementation に含める。
    testImplementation(project(":compiler-plugin:compat-k2000"))
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.shadowJar {
    configurations = listOf(bundled)
    mergeServiceFiles() // META-INF/services/* を結合 (compat の Factory 登録を保持)
    archiveClassifier.set("")
}

// 通常の jar は無効化し、shadow JAR を main artifact として公開する。
// これにより `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` が
// compat-k2000 を同梱した JAR を取得する。
tasks.named<Jar>("jar") {
    enabled = false
}

configurations {
    named("runtimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
    named("apiElements") {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}
