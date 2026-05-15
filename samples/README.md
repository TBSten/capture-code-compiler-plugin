# Capture Code — Samples

Capture Code compiler plugin の **エンドユーザ向け cookbook** をまとめた module 群です。

`integration-test/` 配下が「テスト用 fixture (内部仕様検証)」 であるのに対し、 本 `samples/` 配下は 「ユーザが自分の project にコピーして持っていける最小形」 を意識した作りになっています。

---

## モジュール一覧

| Module | 説明 | 実行 |
| --- | --- | --- |
| [`:samples:jvm-sample`](./jvm-sample/) | JVM-only sample (5 ケース cookbook) | `./gradlew :samples:jvm-sample:run` |
| [`:samples:kmp-sample`](./kmp-sample/) | Kotlin Multiplatform sample (jvm + js + linuxX64 + mingwX64) | `./gradlew :samples:kmp-sample:jvmTest` |

---

## `jvm-sample` の cookbook 一覧

`samples/jvm-sample/src/main/kotlin/me/tbsten/capture/code/sample/jvm/cookbook/` 配下に、 1 ファイル 1 例の形で配置しています。

| ファイル | 例 |
| --- | --- |
| `SimpleMarker.kt` | 単純な marker + `Source` filler |
| `AllFillersSnippet.kt` | `Source` + `SourceLocation` + `CaptureKind` の 3 種を同時に使う |
| `UserDefinedParamsSnippet.kt` | user-defined parameter (`method`, `path` 等) を持つ marker |
| `AllDeclarationTargets.kt` | class / object / function / typealias の全宣言ターゲット |
| `FileAnnotationSample.kt` | `@file:` annotation で **ファイル全体** の source を取得 |

`Main.kt` で 5 ケースを順に `capturedSources<T>()` で集めて stdout に出力します。 各 marker は `Markers.kt` で宣言。

実行例:

```bash
./gradlew :samples:jvm-sample:run
```

---

## `kmp-sample` の構成

KMP では 「marker / use site / `capturedSources<T>()` 呼び出しを **全て test sourceset** に置く」 という **test sourceset 完結方式** を採用しています (`compiler-plugin-design.md` §13 Known Limitations 参照)。

| ファイル | 役割 |
| --- | --- |
| `src/commonTest/.../Markers.kt` | marker 宣言 (`KmpSnippet` / `KmpDetailed` / `KmpFeature`) |
| `src/commonTest/.../UseSites.kt` | marker を付けた宣言 (jvm / js / linuxX64 / mingwX64 から可視) |
| `src/jvmTest/.../KmpSampleTest.kt` | `capturedSources<T>()` を呼んで kotest で検証 |

実行例:

```bash
./gradlew :samples:kmp-sample:jvmTest
# 他 target の compile が通ることも plugin 動作の根拠:
./gradlew :samples:kmp-sample:compileTestKotlinLinuxX64
./gradlew :samples:kmp-sample:compileTestKotlinMingwX64
./gradlew :samples:kmp-sample:compileTestKotlinJs
```

---

## 外部プロジェクトに持ち出すとき (publish 後を想定)

本 sample は **publish 前の現状** に合わせて `kotlinCompilerPluginClasspath(project(":compiler-plugin"))` で compiler plugin を直接 attach しています。

`me.tbsten.capture.code:annotation` / `:gradle-plugin` の Maven Central publish が完了すると、 ユーザは以下の正式パスを使えます:

```kotlin
// build.gradle.kts (JVM)
plugins {
    kotlin("jvm") version "2.0.0"
    id("me.tbsten.capture.code") version "0.1.0"
    application
}

dependencies {
    // gradle-plugin が afterEvaluate で自動追加するため省略可。
    // IDE 補完のため明示推奨。
    implementation("me.tbsten.capture.code:annotation:0.1.0")
}

captureCode {
    includeKdoc = true            // captured source に KDoc を残すか (default: true)
    includeImports = false        // file 起源で `import` 行を含めるか (default: false)
    includeAnnotationLines = false // 宣言頭の @Marker 行を含めるか (default: false)
    dedent = true                  // 共通の先頭インデントを除去するか (default: true)
    includeLineInfo = true         // SourceLocation の start/endLine を埋めるか (default: true)
}
```

KMP の場合:

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.0"
    id("me.tbsten.capture.code") version "0.1.0"
}

kotlin {
    jvm(); js { nodejs() }
    sourceSets {
        commonMain.dependencies {
            implementation("me.tbsten.capture.code:annotation:0.1.0")
        }
    }
}
```

---

## サンプルとテスト fixture の役割分担

| ディレクトリ | 役割 | 配線方式 | 実行 |
| --- | --- | --- | --- |
| `samples/` | ユーザ向け cookbook | `kotlinCompilerPluginClasspath(...)` 直接 attach | `:run` / `:jvmTest` |
| `integration-test/test-jvm/` `test-kmp/` | 仕様検証 fixture (105 ケース) | 同上 | `:test` |
| `integration-test/test-gradle-plugin/` | DSL pathway の真の E2E (`plugins { id("me.tbsten.capture.code") }`) | TestKit + includeBuild | `:test` |
