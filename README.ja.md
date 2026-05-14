# CaptureCode

自分で定義した annotation でコードをマークするだけで、**コンパイル時にソース文字列がキャプチャ**されて runtime にデータとして取り出せる Kotlin compiler plugin。reflection なし、runtime コストゼロ。

```kotlin
import io.github.tbsten.capturecode.*

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippet(
    val source: Source = Source(),
)

@Snippet fun greet() = "Hello!"
@Snippet fun farewell() = "Goodbye!"

fun main() {
    capturedSources<Snippet>().forEach { println(it.source.value) }
    // → fun greet() = "Hello!"
    // → fun farewell() = "Goodbye!"
}
```

`@CaptureCode` を付けた annotation を 1 つ定義する。それでマークした宣言・式・ファイルが対象になる。`capturedSources<T>()` で集めると、各サイトの **ソース文字列がそのまま** 入った annotation インスタンスのリストが返る。

---

## ユーザ定義のパラメータも自由に持てる

filler 型 (`Source` / `SourceLocation` / `CaptureKind`) と、自分で決めたパラメータ (id / label / priority など) は共存できる。filler は型で識別されるので、ユーザ定義の値は use site で指定した値がそのまま保持される。

```kotlin
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class HttpRoute(
    val method: Method,                                 // ← 自分の値
    val path: String,                                   // ← 自分の値
    val source: Source = Source(),                      // ← plugin が埋める
    val location: SourceLocation = SourceLocation(),    // ← plugin が埋める
) {
    enum class Method { GET, POST, PUT, DELETE }
}

@HttpRoute(method = HttpRoute.Method.GET, path = "/users")
fun listUsers() = "[]"

fun main() {
    capturedSources<HttpRoute>().forEach { r ->
        println("${r.method} ${r.path}  @ ${r.location.filePath}:${r.location.startLine}")
        println(r.source.value)
    }
}
```

---

## library 提供の filler 型

marker annotation のパラメータ型として宣言すれば plugin が値を埋める。**宣言しなければ何もしない (opt-in)**。

| 型 | 役割 |
|---|---|
| `Source(val value: String)` | キャプチャされたソース文字列 |
| `SourceLocation(packageName, filePath, startLine, endLine)` | キャプチャサイトの位置情報 |
| `CaptureKind(val value: Kind)` | `EXPRESSION` / `PROPERTY` / `CLASS` / `OBJECT` / `FUNCTION` / `TYPEALIAS` / `FILE` |

---

## キャプチャできる場所

```kotlin
// 宣言
@Marker val prop = 1
@Marker class MyClass
@Marker fun myFun() = 2
@Marker object MyObj
@Marker typealias MyAlias = Int

// ファイル全体
@file:Marker

// 式
val r = @Marker (1 + 2 + 3)
val block = @Marker run { ... }
```

---

## 使い道

- **ドキュメント生成** — コードサンプルを文章とコード本体に二重に書かない
- **CLI コマンド / REST ルートのカタログ**
- **マイグレーションスクリプト・DB スキーマ宣言の収集**
- **フィーチャーフラグ・設定キーの一覧**
- **ベンチマーク・テストフィクスチャの登録**
- **DI binding カタログ** (Anvil / Metro 風)
- **チュートリアルや BDD scenario のステップ収集**

---

## Quick Start

### JVM プロジェクト

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    id("me.tbsten.capture.code") version "<plugin-version>"
}

captureCode {
    includeKdoc = true              // デフォルト: true
    includeImports = false          // デフォルト: false (@file: キャプチャ専用)
    includeAnnotationLines = false  // デフォルト: false
    dedent = true                   // デフォルト: true
    includeLineInfo = true          // デフォルト: true
}

repositories { mavenCentral() }
```

Gradle plugin は `afterEvaluate` で `me.tbsten.capture.code:annotation`
runtime を `implementation` に自動追加するため、 plugin を apply するだけで
良い。

### Kotlin Multiplatform プロジェクト

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.0"
    id("me.tbsten.capture.code") version "<plugin-version>"
}

kotlin {
    jvm()
    js(IR) { browser(); nodejs() }
    wasmJs { browser(); nodejs() }
    linuxX64()
    // Apple target は Xcode 必須 (下記参照)。
}

captureCode {
    dedent = true
}
```

KMP project では annotation runtime が `commonMainImplementation` に自動で
追加される。

> [!IMPORTANT]
> marker 定義 / use site / `capturedSources<T>()` 呼び出しは **同一 Kotlin
> compilation invocation** (同一 source set ツリー) 内に揃える必要がある。
> plugin の in-process registry は invocation スコープ。 test fixture では
> 三者すべてを test sourceset (`commonTest` / `jvmTest` / ...) に置く。
> 詳細は [docs/known-limitations.md](docs/known-limitations.md) を参照。

### サンプル

実行可能なサンプルは [`samples/`](samples/) 配下に置かれる:

- [`samples/jvm-sample/`](samples/jvm-sample/) — 最小 JVM 構成。
- [`samples/kmp-sample/`](samples/kmp-sample/) — test sourceset 完結方式の
  KMP 構成。

---

## アーキテクチャ概要

runtime stub / compiler plugin / Gradle plugin / Kotlin compiler API drift
を吸収する compat layer の 4 層で構成される。

```
:annotation                     // @CaptureCode, filler 型, capturedSources<T>() stub
:compiler-plugin                // FIR + IR extension
  :compiler-plugin:compat       // CompatContext Interface
  :compiler-plugin:compat-k200  // Kotlin 2.0.x 実装
  :compiler-plugin:compat-k210  // Kotlin 2.1.x 実装
:gradle-plugin                  // KotlinCompilerPluginSupportPlugin
```

FIR phase で `@CaptureCode` メタが付いた marker を発見し、 制約 (visibility /
@Retention / @Target / parameter 型) をチェックし、 マーク済み宣言 / 式を
in-process registry に登録する。 IR phase はその registry を読み、
`IrFileEntry` 経由で生ソースを抽出して正規化し、 `capturedSources<T>()`
呼び出しを `listOf(MarkerCtor(...), ...)` のリテラルに書き換える。
inline 展開より前に走るので `@Marker run { ... }` のような式 annotation も
保持される。

詳細は [docs/architecture.md](docs/architecture.md) を参照。

---

## サポート Kotlin バージョン

| Kotlin | ステータス | Compat module |
| ------ | ---------- | ------------- |
| 2.0.x  | サポート (CI で検証) | `compat-k200` |
| 2.1.x  | サポート (CI で検証) | `compat-k210` |
| 2.2.x  | 未検証 — `compat-k210` に fallback + warn | — |
| < 2.0  | **非サポート**。 build 時に明示的にエラー。 | — |

Gradle plugin は consumer project の Kotlin 版を
`KotlinGradlePlugin.getKotlinPluginVersion()` で取得し、 最小サポート版
未満ならエラー、 最大検証版超なら warn を出す。 実 dispatch は同梱
compiler plugin JAR 内の `ServiceLoader` 経由で compile time に行われる。

新 minor の追加手順は
[docs/adding-kotlin-version-support.md](docs/adding-kotlin-version-support.md)
を参照。

---

## 制約

- Kotlin **2.x (K2)** のみ
- marker annotation は **`internal` または `private`** 必須 — 単一モジュール内に閉じた設計
- **KMP 対応** — JVM / JS / Native / WASM。commonMain と各 platform source set で marker 定義・use site・取得が可能

### Known limitations (抜粋)

K2 compiler 由来の制約は plugin 側では吸収せず、 既知の挙動として受け入れる:

1. `@Marker (expr)` には marker の `()` 明示が必要 (`@Marker() (expr)`)。
2. `@Marker () ({ ... })` の lambda 式 annotation は外側 parenthesis を
   含めずキャプチャされる。 `@Marker() run { ... }` 推奨。
3. KMP の `expect` / `actual` 両方に annotation を付けても expect 側は IR
   phase に到達せず、 actual 側 1 件のみキャプチャされる。
4. marker / use site / `capturedSources<T>()` は同一 Kotlin compilation
   invocation 内に置く必要がある。
5. Apple native target は `-PenableAppleTargets=true` の opt-in。

完全な一覧と root-cause 解説は
[docs/known-limitations.md](docs/known-limitations.md)。

---

## ドキュメント

- [docs/architecture.md](docs/architecture.md) — module 構成、 FIR / IR
  pipeline、 Logic A〜H カタログ。
- [docs/known-limitations.md](docs/known-limitations.md) — K2 由来の制約
  リスト。
- [docs/adding-kotlin-version-support.md](docs/adding-kotlin-version-support.md)
  — 新 Kotlin version 対応の手順。
- [compiler-plugin/compat/README.md](compiler-plugin/compat/README.md) —
  compat layer の詳細設計。
- [docs/versioning.md](docs/versioning.md) — semver ポリシー、 pre-1.0 期の
  保証範囲、 release artefact、 SNAPSHOT 取り扱い。
- [CHANGELOG.md](CHANGELOG.md) — version ごとの release notes
  (Keep a Changelog 形式)。

テストカタログは [test-cases.md](test-cases.md) に 100 ケース。

---

## テストの走らせ方

本プロジェクトは 3 層のテストを持ち、 CI (`.github/workflows/ci.yml`) で全てを実行する。 ローカルでは以下のコマンドで個別に再現できる。

```bash
# Compiler plugin の unit test (kctfork で FIR / IR extension を検証)
./gradlew :compiler-plugin:test

# Gradle plugin の sanity test (ProjectBuilder で DSL 配線のみ — 高速)
./gradlew :gradle-plugin:test

# Gradle plugin の真の E2E (TestKit + fixture project で `plugins { id(...) }` を実行 — やや遅い)
./gradlew :integration-test:test-gradle-plugin:test

# JVM-only 統合テスト (ケース #1〜#100)
./gradlew :integration-test:test-jvm:test

# KMP 統合テスト (#101〜#105、 jvm target 上で Kotest 検証)
./gradlew :integration-test:test-kmp:jvmTest

# JVM 以外の KMP target (デフォルトは compile only。 mingwX64 は Unix 上で実行不可)
./gradlew :integration-test:test-kmp:linuxX64Test :integration-test:test-kmp:jsTest :integration-test:test-kmp:wasmJsTest

# 全 non-Apple target の compile (klib / executable)
./gradlew :integration-test:test-kmp:assemble

# Apple target (iOS / macOS) — Xcode 必須
./gradlew :integration-test:test-kmp:assemble -PenableAppleTargets=true
```

`:integration-test:test-kmp` は `jvm` / `js` / `wasmJs` / `linuxX64` / `mingwX64` / (opt-in) Apple native の全 target で compiler plugin を駆動する。 #101〜#105 のケース値検証は jvm target 上で完結し、 他 target は各 `*Test` source set の sanity check (compile + 最小 runtime call) で覆う。

Apple target は `-PenableAppleTargets=true` の opt-in 制御を採用しているため、 Xcode を持たない環境 (ローカル開発 / デフォルト CI runner) でも E2E build が成立する。 完全な Apple matrix 検証は Phase 4 (`task-032`) で macos-latest 系 runner を追加する想定。
