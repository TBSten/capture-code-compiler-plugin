plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
    alias(libs.plugins.shadow)
    id("buildsrc.convention.publish")
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

    // `:compat` は compile 時に必要な API (CaptureCompat / CompilerCompat 等) を提供。
    // shadowJar 経由で同梱されるため、 publish 時の POM には流出させない。
    // `implementation` だと runtimeElements 経由で POM に出てしまい、
    // 受け取った consumer が `me.tbsten.capture.code:compat` を解決しようとして
    // 失敗する。 そのため `compileOnly` + `bundled` の組合せにし、
    // shadowJar には bundled で同梱、 POM 上は依存無しとして扱う。
    compileOnly(project(":compiler-plugin:compat"))
    bundled(project(":compiler-plugin:compat"))

    // 全 compat-kXXX を shadow JAR で同梱する。 バージョン追加時はここに足す。
    bundled(project(":compiler-plugin:compat-k200"))
    bundled(project(":compiler-plugin:compat-k210"))

    testImplementation(project(":annotation"))
    // Unit test では shadow JAR ではなく素の classpath を使うので、
    // compat 実装モジュールも明示的に testImplementation に含める。
    // main の `compat` は `compileOnly` + `bundled` で扱っているため、
    // test classpath には別途 `:compat` を追加する必要がある。
    testImplementation(project(":compiler-plugin:compat"))
    testImplementation(project(":compiler-plugin:compat-k200"))
    testImplementation(project(":compiler-plugin:compat-k210"))
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
// compat-k200 / compat-k210 を同梱した JAR を取得する。
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

// ----------------------------------------------------------------------------
// Maven Central publish 設定
//
// `:compiler-plugin` の main artifact は **shadowJar** (compat-k200 / compat-k210
// を同梱した一枚 JAR)。 上の `runtimeElements` / `apiElements` の outgoing
// artifact 差し替えにより、 `components["java"]` (vanniktech が `KotlinJvm`
// configuration で参照する) も shadow JAR を指すようになっている。
//
// POM dependencies に shadowed (bundled) module が漏れない理由:
//   - `bundled` configuration は `runtimeElements` / `apiElements` に紐づかない
//     独立 configuration なので、 POM の `<dependencies>` には出力されない。
//   - `implementation(project(":compiler-plugin:compat"))` のみ
//     `runtimeElements` 経由で POM に出る可能性があるが、 当該 module は
//     publish しないため、 ユーザは取得できない。 後の `compat` 自体は
//     shadowJar に同梱済なので runtime 上は問題なし。 ただし POM に出ると
//     consumer が解決失敗するため、 dependencies 漏れを避ける措置として
//     `compat` 依存も `bundled` 同等扱いにする (= compileOnly + bundled)。
//
// 上記の依存方針変更は `dependencies { implementation(project(":compiler-plugin:
// compat")) }` から `compileOnly(...)` + `bundled(...)` に切り替えることで実現。
// ----------------------------------------------------------------------------
mavenPublishing {
    // coordinates は `project.group:project.name:project.version` (root 集約値) を
    // そのまま使用 (`:annotation` と同じ理由 — SSOT 維持)。
    pom {
        name.set("Capture Code compiler plugin")
        description.set("Kotlin compiler plugin artifact (shadow JAR with bundled compat modules) for capturing source code into runtime")
    }
}
