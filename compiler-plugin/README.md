# compiler-plugin module

Capture Code Kotlin Compiler Plugin の本体モジュール。 `:compiler-plugin` (main module) + `:compiler-plugin:compat` (共有 SPI) + `:compiler-plugin:compat-k{XXX}` (バージョン別 impl) の 3 層構成で、 shadow JAR で 1 つの publish artifact (`me.tbsten.capture.code:compiler-plugin`) にまとめている。

user-facing な機能紹介と quick-start は repo root の [`README.MD`](../README.MD) / [`README.ja.md`](../README.ja.md)、 アーキテクチャ全体像と各 Logic の責務マッピングは [`docs/architecture.md`](../docs/architecture.md) を参照。 本ドキュメントは **`compiler-plugin/` module 内に新しいコードを追加するときに「どこに何を置くか」を決めるための実装ガイド**。

## Overview

本 plugin は FIR phase + IR phase の 2 段構成で動く:

- **FIR phase**: `@CaptureCode` メタ annotation 付きの marker class 探索、 marker annotation の制約検査 (visibility / `@Retention` / `@Target` / parameter 型 等)、 `capturedSources<T>()` 呼び出しの型引数 `T` 検査、 `@Marker(expr)` 式 annotation site の収集
- **IR phase**: `capturedSources<T>()` 呼び出しを実 IR (collected `List<T>` の組み立て) に置換、 `Source` / `SourceLocation` / `CaptureKind` filler の組み立て、 normalize chain (dedent / kdoc strip / annotation line strip / package & import strip / blank trim) の適用

runtime drift (FIR checker `check()` の引数順が Kotlin minor バージョン間で変わる等の non-binary-compatible API drift) を完全に排除するため、 **main module は Kotlin 2.0.0 固定 API で compile** され、 FIR Checker / IR Transform の実装本体は全て **`compat-kXXX/` のバージョン別 module** に置かれている。 これにより consumer Kotlin が 2.2.x+ に bump されても main module 自身は runtime drift から完全に隔離される。

## Source structure

```text
compiler-plugin/
├── README.md                                  # 本ファイル — module 内部実装ガイド
├── build.gradle.kts                            # shadowJar で bundled compat-kXXX を同梱、 jar task は無効化
│
├── src/main/kotlin/me/tbsten/capture/code/    # ← エントリポイント (4 ファイルだけ)
│   ├── CaptureCodeCompilerPluginRegistrar.kt   #   CompilerPluginRegistrar (supportsK2 = true)
│   ├── CaptureCodeCommandLineProcessor.kt      #   CLI option → CaptureCodePluginConfig
│   ├── CaptureCodeFirExtensionRegistrar.kt     #   compat.firAdditionalCheckersExtensions() を登録
│   └── CaptureCodeIrExtension.kt               #   compat.transformIr(...) に委譲 + registry reset
│
├── src/test/kotlin/me/tbsten/capture/code/    # ← kctfork ベースの unit test
│   ├── CaptureCodeCompilerPluginTest.kt        #   plugin 全体の registration sanity
│   ├── CommandLineProcessorTest.kt
│   ├── fir/
│   │   ├── MarkerAnnotationCheckerTest.kt
│   │   └── CapturedSourcesCallCheckerTest.kt
│   ├── feature/
│   │   ├── capturedSources/                    # filler / file annotation / per-marker option
│   │   │   └── ir/normalize/SourceNormalizerTest.kt
│   │   └── capturedExpression/                 # @Marker(expr) 式 annotation の収集 test
│   └── spike/                                  # design 検証用 spike (本番 plugin と別)
│
├── compat/                                     # :compiler-plugin:compat — 共有 SPI + domain SSoT
│   ├── README.md                               #   compat layer 設計 + 新 Kotlin バージョン追加手順
│   └── src/main/kotlin/me/tbsten/capture/code/
│       ├── CaptureCodePluginConfig.kt          #   5 つの CLI option を集約する data class
│       ├── compat/                             #   バージョン差吸収 SPI (domain 知識禁止ゾーン)
│       │   ├── CompatContext.kt                #     interface + Companion.load (ServiceLoader)
│       │   ├── CaptureCodeCompatHolder.kt      #     process-scoped lazy holder
│       │   ├── KotlinToolingVersion.kt         #     dev / Beta-RC track を区別する parser + resolver
│       │   ├── CapturedSite.kt                 #     IR phase の marker site data class
│       │   ├── CaptureCodeMarkerRegistry.kt    #     FIR → IR の compilation-scoped holder
│       │   ├── CaptureCodeExpressionSiteRegistry.kt
│       │   └── CaptureCodeMarkerOptions.kt     #     per-marker option (includeKdoc/dedent/...)
│       ├── error/                              #   plugin 横断 Diagnostic 用 ClassId 等の補助 SSoT
│       │   └── CaptureCodeFillerClassIds.kt        #     Source / SourceLocation / CaptureKind の ClassId
│       │                                       #     ※ 文面 SSoT は main の `feature/.../*Errors.kt` (English-only, task-122)
│       ├── feature/
│       │   ├── capturedSources/normalize/      #   pure 関数の normalize chain
│       │   │   ├── SourceNormalizer.kt              #     orchestrator
│       │   │   ├── Dedent.kt / KdocStrip.kt / BlankTrim.kt
│       │   │   ├── AnnotationLineStrip.kt / PackageImportStrip.kt
│       │   │   ├── KDocLookup.kt / NormalizeOptions.kt
│       │   │   └── CaptureCodePluginConfigBridge.kt
│       │   └── capturedsources/
│       │       └── CaptureCodeCallableIds.kt    #     `capturedSources<T>()` の CallableId SSoT
│       └── fir/marker/                          #   `@CaptureCode` メタ annotation の domain
│           ├── CaptureCodeMetaAnnotation.kt     #     ClassId / FqName / predicate
│           └── CaptureCodeMarkerOptionsExtractor.kt #     marker option 抽出
│
├── compat-k200/                                # Kotlin 2.0.x 専用 impl (baseline)
│   └── src/main/kotlin/me/tbsten/capture/code/compat/k200/
│       ├── CompatContextImpl.kt                #   AutoService + Factory(minVersion=2.0.0)
│       │                                       #   nested K200Diagnostics に KtDiagnosticFactory* + renderer を集約 (task-121)
│       ├── K200IrTransform.kt                  #   IR phase orchestrator
│       ├── K200CapturedSourcesCollector.kt     #   Logic B (IR side)
│       ├── K200CapturedSourcesRewriter.kt      #   Logic H (`capturedSources<T>()` 置換)
│       ├── SourceTextExtractor.kt              #   PSI → source text (version-sensitive)
│       ├── checker/                            #   FIR Checker (Logic A / F / G / B-fir)
│       │   ├── K200CaptureCodeMarkerClassChecker.kt
│       │   ├── K200CapturedSourcesCallChecker.kt
│       │   ├── K200MarkerAnnotationChecker.kt
│       │   ├── K200ExpressionSiteCollector.kt
│       │   └── K200CheckerExtensions.kt        #     FirAdditionalCheckersExtension 束ね
│       ├── filler/                             #   IR filler builder (Logic D)
│       │   ├── FillerBuilder.kt
│       │   ├── CaptureKindFillerBuilder.kt
│       │   ├── SourceFillerBuilder.kt
│       │   └── SourceLocationFillerBuilder.kt
│       └── userargs/                           #   @Marker(arg=...) を IR で組み立て
│           ├── UserArgIrBuilder.kt
│           └── UserArgPrimitiveIrBuilder.kt
│
└── compat-k210/                                # Kotlin 2.1.x 専用 impl (compat-k200 と同形)
    └── src/main/kotlin/me/tbsten/capture/code/compat/k210/
        └── ...                                 #   K210* prefix 付きで compat-k200 と対称構造
```

## Feature / logic 用語

| 用語 | 内容 |
| --- | --- |
| **feature** | ユーザ目線の機能単位。 ディレクトリ名は lowerCamelCase の機能名 (例: `capturedSources`, `capturedExpression`, `capturedsources` の callable identity) |
| **logic** | feature を構成する plugin 動作単位。 本プロジェクトでは FIR Checker 1 つ / IR Transformer 1 つ単位が "logic" の粒度 (コード KDoc 内では Logic A–I の符号で参照される) |
| **sub-logic** | logic 内の更に細かい操作単位 (例: `feature/capturedSources/ir/normalize/` 配下の Dedent / KdocStrip) |

ディレクトリ vs domain 知識の境界 (新規ファイル配置の判定基準):

| directory | plugin の domain 知識 OK? | 入れていいもの |
| --- | --- | --- |
| `compiler-plugin/src/main/.../code/` (root 直下) | **OK だが組み立てのみ** | `CompilerPluginRegistrar` / `CommandLineProcessor` / `FirExtensionRegistrar` / `IrGenerationExtension` だけ |
| `compat/.../compat/` | **NG** | Kotlin バージョン差吸収 SPI のみ。 `@CaptureCode` FQN や normalize ロジックは禁止 |
| `compat/.../error/` | **OK (plugin 全体)** | filler 型の ClassId など compat 共有 SSoT。 task-122 以降、 diagnostic 文面 SSoT は main の `feature/.../*Errors.kt` (English-only) に統一済 |
| `compat/.../feature/<feature>/` 直下 | **その feature の domain のみ** | `CallableId` / FQN SSoT, 共有 data class |
| `compat/.../feature/<feature>/<sub>/` | **その sub-logic に閉じた domain** | pure 関数の normalize 等。 compat-kXXX 間で共有可能なロジック |
| `compat/.../fir/marker/` | **`@CaptureCode` の domain** | meta annotation の ClassId / FqName / option extractor (FIR API drift から独立した部分) |
| `compat-kXXX/.../checker/` `filler/` `userargs/` | **OK だがバージョン依存** | `FirChecker` 継承 / IR builder / IR userargs 等の処理本体。 必ず `K<XXX>` prefix |
| `src/test/.../feature/<feature>/` | **その feature** | unit test は main / compat と対称な階層で配置 |

## Compat layer

Kotlin Compiler Plugin API は **stable でなく minor / patch でも signature drift する**。 本 plugin は以下の戦略で drift を吸収する:

1. **main module は Kotlin 2.0.0 固定 API で compile** (`compileOnly(libs.kotlin.compiler.embeddable.k200)`) — 2.0.0 baseline で生成された bytecode が runtime 上で見つけられる API のみを参照する
2. **共有 SPI** (`:compiler-plugin:compat`) — `CompatContext` interface で drift のあるメソッドを宣言。 全 `compat-kXXX/` から `compileOnly` で参照される
3. **バージョン別 impl** (`:compiler-plugin:compat-k200`, `:compiler-plugin:compat-k210`, ...) — それぞれ `kotlin-compiler-embeddable-k<XXX>` を `compileOnly` でピンし、 そのバージョン専用の FIR Checker / IR Transform を実装。 `@AutoService(CompatContext.Factory::class)` で自動的に `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録
4. **ShadowJar 同梱** — main module の `bundled` configuration に全 `compat-kXXX/` を入れ、 `tasks.shadowJar { mergeServiceFiles() }` で service file を結合。 publish される single artifact は全バージョンの impl を含む
5. **ServiceLoader runtime 選択** — `CaptureCodeCompatHolder.context` (process-scoped lazy) が `ServiceLoader<CompatContext.Factory>` を走らせ、 `minVersion <= currentVersion` を満たす Factory の中で最大の `minVersion` を選ぶ。 `dev` / `Beta` / `RC` の track 区別も `KotlinToolingVersion` resolver が担当

新しい Kotlin バージョンへの対応手順は [`compat/README.md`](compat/README.md) の "Adding Support for New Kotlin Versions" を参照。 `generate-compat-module.sh` でスケルトン生成できる。

## Build / dependency 配線

- `build.gradle.kts` 主要設定:
    - `compileOnly(libs.kotlin.compiler.embeddable.k200)` — main は 2.0.0 baseline 固定
    - `compileOnly(project(":compiler-plugin:compat"))` + `bundled(project(":compiler-plugin:compat"))` — compat を POM に流出させず shadowJar に同梱
    - `bundled(project(":compiler-plugin:compat-k200"))` + `bundled(project(":compiler-plugin:compat-k210"))` — 全 compat-kXXX を shadow JAR で同梱。 新バージョン追加時はここに足す
    - `tasks.shadowJar { mergeServiceFiles(); archiveClassifier.set("") }` — META-INF/services を結合し、 classifier なし main artifact 化
    - `tasks.named<Jar>("jar") { enabled = false }` — 通常 jar は無効化。 `runtimeElements` / `apiElements` の outgoing artifact も shadowJar に差し替え
- KSP + AutoService で `META-INF/services/` を自動生成 (resources/ には書かない)

## Test

| test set | 場所 | 内容 |
| --- | --- | --- |
| Unit test (kctfork + Kotest) | `compiler-plugin/src/test/` | plugin 全体 (registration / CLI / FIR checker / IR filler / normalize) を kctfork の `KotlinCompilation` で実行 |
| Compat sanity test | `compat-kXXX/src/test/` | 各 `compat-kXXX` の `CompatContextImpl` が `kotlin-compiler-embeddable-k<XXX>` 上で実体化できるかの sanity check (`K{XXX}CompatContextSanityTest.kt`) |
| Integration test | repo root の `:integration-test` モジュール | Gradle plugin (`:gradle-plugin`) 経由で publish 済み plugin を組み込み、 実 Kotlin compile 出力を検証 |

CI matrix (`.github/workflows/`) は全 supported Kotlin バージョンに対して compat module を回し、 drift を早期検出する。

## 関連ドキュメント

- [`compat/README.md`](compat/README.md) — compat layer の SPI 詳細、 ServiceLoader 解決アルゴリズム、 新バージョン追加手順
- [`docs/architecture.md`](../docs/architecture.md) — プロジェクト全体のアーキテクチャ俯瞰
- [`docs/adding-kotlin-version-support.md`](../docs/adding-kotlin-version-support.md) — 新 Kotlin バージョン対応の checklist
- [`docs/versioning.md`](../docs/versioning.md) — supported Kotlin バージョンの選定方針
- [`.claude/rules/RULES.md`](../.claude/rules/RULES.md) — Claude Code 用 module 配置ルール (本 README の判定基準を rule として export)
