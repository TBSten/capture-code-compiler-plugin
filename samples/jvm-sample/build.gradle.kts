// ----------------------------------------------------------------------------
// JVM-only sample for the Capture Code compiler plugin.
//
// 本 module は **エンドユーザ向け cookbook** であり、 「ユーザが自分の project に
// そのままコピーして持っていける最小形」 を意識して書いている。
//
// ## 外部プロジェクトに持っていくときの差し替え手順 (publish 後を想定)
//
//   plugins {
//       kotlin("jvm") version "2.0.0"
//       id("me.tbsten.capture.code") version "0.1.0-SNAPSHOT"
//       application
//   }
//
//   dependencies {
//       implementation("me.tbsten.capture.code:annotation:0.1.0-SNAPSHOT")
//       // ↑ gradle-plugin が afterEvaluate で自動追加もする (IDE 補完のため明示推奨)
//   }
//
//   captureCode {
//       includeKdoc = true
//       includeImports = false
//       includeAnnotationLines = false
//       dedent = true
//       includeLineInfo = true
//   }
//
// `me.tbsten.capture.code:annotation` / `:gradle-plugin` の Maven Central publish
// が完了すると、 上記の形が動く。
//
// ## 本 module (publish 前) での記述方針
//
// `:gradle-plugin` がまだ Maven 経由で取れないため、 `plugins { id(...) }` の
// 解決ができない。 そこで本 sample では integration-test/test-jvm と同様、
// `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` で直接 compiler
// plugin を attach する。 plugin の動作 (capturedSources の rewrite 等) は
// 完全に同等であり、 DSL extension が使えない点だけが差分。
//
// DSL option を変更したいときは `freeCompilerArgs` で渡す:
//
//   tasks.withType<KotlinCompile>().configureEach {
//       compilerOptions.freeCompilerArgs.addAll(
//           "-P", "plugin:me.tbsten.capture.code:dedent=false",
//       )
//   }
// ----------------------------------------------------------------------------
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":annotation"))
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}

application {
    mainClass = "me.tbsten.capture.code.sample.jvm.MainKt"
}

// DSL option を CLI で指定する例 (publish 後は `captureCode { ... }` block を使える)。
// 下記はデフォルト値の例示で、 動作には影響しない。
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-P", "plugin:me.tbsten.capture.code:includeKdoc=true",
        "-P", "plugin:me.tbsten.capture.code:includeImports=false",
        "-P", "plugin:me.tbsten.capture.code:includeAnnotationLines=false",
        "-P", "plugin:me.tbsten.capture.code:dedent=true",
        "-P", "plugin:me.tbsten.capture.code:includeLineInfo=true",
    )
}
