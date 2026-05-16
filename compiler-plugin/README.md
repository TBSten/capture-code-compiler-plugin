# compiler-plugin module

Capture Code Kotlin Compiler Plugin の本体モジュール。 `:compiler-plugin` (main module) + `:compiler-plugin:compat` (共有 SPI) + `:compiler-plugin:compat-k{XXX}` (バージョン別 impl) の 3 層構成で、 shadow JAR で 1 つの publish artifact (`me.tbsten.capture.code:compiler-plugin`) にまとめている。

user-facing な機能紹介と quick-start は repo root の [`README.MD`](../README.MD) / [`README.ja.md`](../README.ja.md)、 アーキテクチャ全体像と各 Logic の責務マッピングは [`docs/architecture.md`](../docs/architecture.md) を参照。 本ドキュメントは **`compiler-plugin/` module 内に新しいコードを追加するときに「どこに何を置くか」を決めるための実装ガイド**。

## Overview

本 plugin は FIR phase + IR phase の 2 段構成で動く。 各 Logic は `docs/architecture.md` の "Logic catalogue (A – H)" と対応:

- **FIR phase** (Logic A / F / G / B-fir):
    - Logic A: `@CaptureCode` メタ annotation 付きの marker class 探索 + `CaptureCodeMarkerRegistry` 登録
    - Logic F: marker annotation の制約検査 (`expect` 禁止 / 引数型 / filler default ...)
    - Logic G: `capturedSources<T>()` 呼び出しの型引数 `T` 検査
    - Logic B-fir: `@Marker (expr)` 式 annotation site の収集 (`CaptureCodeExpressionSiteRegistry`)
- **IR phase** (Logic B-ir / C / D / H):
    - Logic B-ir: 宣言箇所 (class / property / function / file annotation 等) の collection
    - Logic C: source text retrieval (IrFileEntry 経由 + PSI fallback、 `compat.loadFileText`)
    - Logic D: source normalization chain (`Dedent` / `KdocStrip` / `BlankTrim` / `AnnotationLineStrip` / `PackageImportStrip`)
    - Logic H: `capturedSources<T>()` を `listOf(MarkerCtor(Source(...), SourceLocation(...), CaptureKind(...), userArgs...))` に置換

```
                ┌─────────────────────────────────────────────┐
                │ FIR phase (compat-kXXX の checker class)    │
                │                                             │
   .kt files ──▶│  A. Marker class discovery                  │
                │      └── CaptureCodeMarkerRegistry          │
                │  F. Marker annotation checker (expect /     │
                │     param type / filler default)            │
                │  G. capturedSources<T>() type-arg checker   │
                │  B-fir. Expression-site collector           │
                │      └── CaptureCodeExpressionSiteRegistry  │
                └─────────────────────────────────────────────┘
                              │
                              ▼
                ┌─────────────────────────────────────────────┐
                │ IR phase (compat-kXXX の K{XXX}IrTransform) │
                │                                             │
                │  B-ir. Declaration-site collection          │
                │  C.    Source text retrieval (IrFileEntry,  │
                │        PSI fallback)                        │
                │  D.    Normalize chain (Dedent / KdocStrip  │
                │        / BlankTrim / ...)                   │
                │  H.    capturedSources<T>() rewrite         │
                │        └── replaces IrCall with             │
                │            listOf(MarkerCtor(...), …)       │
                └─────────────────────────────────────────────┘
                              │
                              ▼
                        .class / .klib
```

runtime drift (FIR checker `check()` の引数順が Kotlin minor バージョン間で変わる等の non-binary-compatible API drift) を完全に排除するため、 **main module は Kotlin 2.0.0 固定 API で compile** され、 FIR Checker / IR Transform の処理本体は **`compat-kXXX/` のバージョン別 module** に置かれている。 これにより consumer Kotlin が 2.2.x+ に bump されても main module 自身は runtime drift から完全に隔離される。

task-118 以降は domain SSoT (CallableId / ClassId / `<Logic>Errors.kt` / `<Logic>Warnings.kt` / pure normalize 関数) は main 側 `feature/` 配下に集約され、 各 `compat-kXXX/` は `mainClassesOnly` outgoing variant 経由で main module の compile output を `compileOnly` 参照する。 詳細は [docs/architecture.md](../docs/architecture.md) の "Compat layer" 節を参照。

## Source structure

```text
compiler-plugin/
├── README.md                                  # 本ファイル — module 内部実装ガイド
├── build.gradle.kts                            # shadowJar で bundled compat-kXXX を同梱、 jar task は無効化
│                                                  # `mainClassesOnly` / `mainRuntimeClassesOnly` outgoing
│                                                  # variant で main の compile output を compat 側に流す
│
├── src/main/kotlin/me/tbsten/capture/code/    # ← 主要処理は全てこちら (Kotlin 2.0.0 baseline)
│   ├── CaptureCodeCompilerPluginRegistrar.kt   #   CompilerPluginRegistrar (supportsK2 = true)
│   ├── CaptureCodeCommandLineProcessor.kt      #   CLI option → CaptureCodePluginConfig
│   ├── CaptureCodeFirExtensionRegistrar.kt     #   compat.firAdditionalCheckersExtensions() を登録
│   ├── CaptureCodeIrExtension.kt               #   compat.transformIr(...) に委譲 + registry reset
│   │
│   ├── error/                                  #   plugin 横断 Error / Diagnostic SPI (task-121)
│   │   ├── CaptureCodeCompilerPluginError.kt   #     interface (id / message / reply)
│   │   ├── ErrorContextDsl.kt                  #     ad-hoc Error 組み立て DSL
│   │   ├── Errors.kt / Replies.kt              #     cross-cutting SSoT (現状空)
│   │   └── ReportError.kt                      #     DiagnosticReporter.reportError(...) 拡張
│   │
│   ├── warning/                                #   plugin 横断 Warning SPI (task-121)
│   │   ├── CaptureCodeCompilerPluginWarning.kt #     interface
│   │   ├── WarningContextDsl.kt
│   │   ├── Warnings.kt
│   │   └── ReportWarning.kt
│   │
│   └── feature/                                #   feature 単位の domain (main 側)
│       ├── markerDefinition/                   #   `@CaptureCode` メタ annotation
│       │   ├── CaptureCodeFillerClassIds.kt    #     Source / SourceLocation / CaptureKind ClassId
│       │   ├── CaptureCodeMarkerOptions.kt     #     per-marker option (includeKdoc/dedent/...)
│       │   ├── CaptureCodeMarkerRegistry.kt    #     FIR → IR compilation-scoped holder
│       │   ├── CaptureCodeMetaAnnotation.kt    #     ClassId / FqName / predicate
│       │   ├── MarkerDefinitionWarnings.kt     #     cross-logic warning SSoT
│       │   ├── fir/                            #     Logic A / F
│       │   │   ├── discoverMarkerClass/        #       Logic A (marker class 発見)
│       │   │   │   ├── DiscoverMarkerClass.kt
│       │   │   │   └── extractMarkerOptions/ExtractMarkerOptions.kt
│       │   │   └── validateMarkerAnnotation/   #       Logic F (制約検査)
│       │   │       ├── ValidateMarkerAnnotation.kt
│       │   │       ├── MarkerAnnotationErrors.kt   #   English-only diagnostic 文面 SSoT
│       │   │       ├── MarkerAnnotationWarnings.kt
│       │   │       └── warnIfOverrideNoEffect/WarnIfOverrideNoEffect.kt
│       │   └── ir/                             #     duplicate / unused param warning
│       │       ├── warnIfDuplicateMarkerFqn/WarnIfDuplicateMarkerFqn.kt
│       │       └── warnIfParameterUnused/WarnIfParameterUnused.kt
│       │
│       └── capturedSources/                    #   `capturedSources<T>()` pipeline
│           ├── CaptureCodeCallableIds.kt        #     CallableId SSoT
│           ├── CaptureCodeExpressionSiteRegistry.kt
│           ├── CapturedSite.kt                  #     marker site data class
│           ├── fir/                             #     Logic G / B-fir
│           │   ├── validateCapturedSourcesCall/
│           │   │   ├── ValidateCapturedSourcesCall.kt
│           │   │   └── CapturedSourcesCallErrors.kt
│           │   └── collectExpressionSite/CollectExpressionSite.kt
│           └── ir/                              #     Logic B-ir / C / D / H
│               ├── collectDeclarationSite/CollectDeclarationSite.kt
│               ├── extractSourceText/ExtractSourceText.kt
│               ├── normalize/                   #     Logic D (pure 関数)
│               │   ├── SourceNormalizer.kt
│               │   ├── Dedent.kt / KdocStrip.kt / BlankTrim.kt
│               │   ├── AnnotationLineStrip.kt / PackageImportStrip.kt
│               │   ├── KDocLookup.kt / NormalizeOptions.kt
│               │   └── CaptureCodePluginConfigBridge.kt
│               └── rewriteCapturedSourcesCall/  #     Logic H
│                   ├── RewriteCapturedSourcesCall.kt
│                   ├── buildMarkerInstance/
│                   │   ├── BuildMarkerInstance.kt
│                   │   ├── filler/{BuildFiller,FillSource,FillSourceLocation,FillCaptureKind}.kt
│                   │   └── userargs/{BuildUserArg,BuildUserArgPrimitive}.kt
│                   ├── warnIfNoMarkerFound/WarnIfNoMarkerFound.kt
│                   └── CapturedSourcesWarnings.kt
│
├── src/test/kotlin/me/tbsten/capture/code/    # ← kctfork ベースの unit test
│
├── compat/                                     # :compiler-plugin:compat — 共有 SPI のみ (domain 知識禁止)
│   ├── README.md                               #   compat layer 設計 + 新 Kotlin バージョン追加手順
│   └── src/main/kotlin/me/tbsten/capture/code/
│       ├── CaptureCodePluginConfig.kt          #   5 つの CLI option を集約する data class
│       └── compat/                             #   バージョン差吸収 SPI
│           ├── CompatContext.kt                #     interface + Companion.load (ServiceLoader)
│           ├── CaptureCodeCompatHolder.kt      #     process-scoped lazy holder
│           ├── CaptureCodePluginConfigHolder.kt #    plugin config holder
│           └── KotlinToolingVersion.kt         #     dev / Beta-RC track 区別 parser + resolver
│
├── compat-k200/                                # Kotlin 2.0.0 専用 impl (baseline、 12 .kt)
│   └── src/main/kotlin/me/tbsten/capture/code/compat/k200/
│       ├── CompatContextImpl.kt                #   AutoService + Factory(minVersion=2.0.0)
│       │                                       #   nested K200Diagnostics に KtDiagnosticFactory* + renderer
│       ├── K200IrTransform.kt                  #   IR phase orchestrator
│       ├── K200CapturedSourcesCollector.kt     #   IR Logic B-ir
│       ├── K200CapturedSourcesRewriter.kt      #   IR Logic H
│       ├── SourceTextExtractor.kt              #   PSI → source text (version-sensitive)
│       ├── checker/                            #   FIR Checker (Logic A / F / G / B-fir)
│       │   ├── K200CheckerExtensions.kt
│       │   └── FullyExpandedTypeShim.kt        #     drift D11 吸収 (reflection shim)
│       ├── filler/{CaptureKindFillerBuilder,FillerBuilder,SourceFillerBuilder,SourceLocationFillerBuilder}.kt
│       └── userargs/{UserArgIrBuilder,UserArgPrimitiveIrBuilder}.kt
│
├── compat-k202/                                # Kotlin 2.0.10..2.0.21 (12 .kt、 k200 と対称)
├── compat-k210/                                # Kotlin 2.1.x (12 .kt、 K210* prefix)
│
├── compat-k220/                                # Kotlin 2.2.x (13 .kt + 4 .java Shim)
│   └── src/main/
│       ├── kotlin/.../compat/k220/             #   K220* prefix (k200 と同構造)
│       └── java/.../compat/k220/checker/       #   FIR Checker signature drift D9 用 Java shim
│           ├── K220CaptureCodeMarkerClassCheckerShim.java
│           ├── K220CapturedSourcesCallCheckerShim.java
│           ├── K220ExpressionSiteCollectorShim.java
│           └── K220MarkerAnnotationCheckerShim.java
│
├── compat-k230/                                # Kotlin 2.3.x (13 .kt + 5 .java Shim)
│   └── src/main/                               #   K230RendererMapShim.java は static init NPE 回避用 (by lazy 化)
│
└── compat-k240rc/                              # Kotlin 2.4.0-RC{,N} (13 .kt + 5 .java Shim)
```

各 `compat-kXXX/` は task-118 で導入された `mainClassesOnly` outgoing variant 経由で main module の compile output を `compileOnly` 参照する (shadowJar を経由しないので循環依存を回避)。 さらに task-122 で `mainRuntimeClassesOnly` (`Usage.JAVA_RUNTIME` / `LibraryElements.JAR`) を追加し、 nested `K{XXX}Diagnostics` が `<clinit>` で main 側 `MarkerAnnotationErrors.IS_EXPECT.message` 等を読む test scenario を成立させている。

## Feature / logic 用語

| 用語 | 内容 |
| --- | --- |
| **feature** | ユーザ目線の機能単位。 ディレクトリ名は lowerCamelCase (現状は `markerDefinition` と `capturedSources` の 2 feature) |
| **logic** | feature を構成する plugin 動作単位。 本プロジェクトでは FIR Checker 1 つ / IR Transformer 1 つ単位が "logic" の粒度。 コード KDoc 内では Logic A–H の符号で参照される (`docs/architecture.md` 参照) |
| **sub-logic** | logic 内の更に細かい操作単位 (例: `feature/capturedSources/ir/normalize/` 配下の Dedent / KdocStrip) |

ディレクトリ vs domain 知識の境界 (新規ファイル配置の判定基準。 詳細は [`.claude/rules/`](../.claude/rules/) の各 rule ファイル):

| directory | plugin の domain 知識 OK? | 入れていいもの |
| --- | --- | --- |
| `compiler-plugin/src/main/.../code/` (root 直下) | **OK だが組み立てのみ** | `CompilerPluginRegistrar` / `CommandLineProcessor` / `FirExtensionRegistrar` / `IrGenerationExtension` だけ |
| `compiler-plugin/src/main/.../error/` | **OK (plugin 全体)** | `CaptureCodeCompilerPluginError` interface / DSL / `ReportError` 拡張 (task-121 で main 側に新設) |
| `compiler-plugin/src/main/.../warning/` | **OK (plugin 全体)** | `CaptureCodeCompilerPluginWarning` interface / DSL / `ReportWarning` 拡張 (task-121 で main 側に新設) |
| `compiler-plugin/src/main/.../feature/<feature>/` 直下 | **その feature の domain** | `CallableId` / `ClassId` / FQN SSoT、 marker option data class、 registry object (task-118 で main 側に統合) |
| `compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/` | **その logic に閉じた domain** | pure normalize 関数、 `<Logic>Errors.kt` / `<Logic>Warnings.kt` (English-only 文面 SSoT) |
| `compiler-plugin/compat/.../compat/` | **NG** | バージョン差吸収 SPI のみ (`CompatContext` / `CaptureCodeCompatHolder` / `CaptureCodePluginConfigHolder` / `KotlinToolingVersion`)。 `@CaptureCode` FQN や normalize ロジックは禁止 |
| `compiler-plugin/compat-kXXX/.../checker/` `filler/` `userargs/` | **OK だがバージョン依存** | `FirChecker` 継承 / IR builder / IR userargs 等の処理本体。 必ず `K<XXX>` prefix。 task-122+ では Java Shim も同居 (D9 FIR Checker signature drift 吸収) |
| `compiler-plugin/compat-kXXX/.../CompatContextImpl.kt` の nested `K{XXX}Diagnostics` | **OK だがバージョン依存** | `KtDiagnosticFactory*` 宣言 + renderer chain (`MAP`)。 文面は main 側 `<Logic>Errors.kt` の `.message` を参照 |
| `compiler-plugin/src/test/.../feature/<feature>/` | **その feature** | unit test は main / compat と対称な階層で配置 |

## Compat layer

Kotlin Compiler Plugin API は **stable でなく minor / patch でも signature drift する**。 本 plugin は以下の戦略で drift を吸収する:

1. **main module は Kotlin 2.0.0 固定 API で compile** (`compileOnly(libs.kotlin.compiler.embeddable.k200)`) — 2.0.0 baseline で生成された bytecode が runtime 上で見つけられる API のみを参照する
2. **共有 SPI** (`:compiler-plugin:compat`) — `CompatContext` interface で drift のあるメソッドを宣言 (`transformIr` / `firAdditionalCheckersExtensions` / `registerExtensions` / `containingFilePathOf` / `fullyExpandedTypeOf` / `loadFileText` / `diagnosticFactory` 等)。 task-119+ で additive method が増加。 全 `compat-kXXX/` から `compileOnly` で参照される
3. **バージョン別 impl** (`:compiler-plugin:compat-k200` / `compat-k202` / `compat-k210` / `compat-k220` / `compat-k230` / `compat-k240rc`) — それぞれ `kotlin-compiler-embeddable-k<XXX>` を `compileOnly` でピンし、 そのバージョン専用の FIR Checker (Logic A / F / G / B-fir) + IR Transform (Logic B-ir / C / D / H) + nested `K{XXX}Diagnostics` を実装。 `@AutoService(CompatContext.Factory::class)` で自動的に `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録。 IR logic は drift D5–D8 が live のため `compat-kXXX/` に残置 (task-120-B case C)
4. **mainClassesOnly variant** (task-118) — main module の `compileKotlin` output を専用 outgoing variant で expose し、 各 `compat-kXXX/` が `compileOnly` でこれを引く (shadowJar 経由の循環依存を避ける)。 runtime classpath は shadowJar に main の class が同梱されるため別ルートで解決
5. **ShadowJar 同梱** — main module の `bundled` configuration に全 `compat-kXXX/` を入れ、 `tasks.shadowJar { mergeServiceFiles() }` で service file を結合。 publish される single artifact は全バージョンの impl を含む
6. **ServiceLoader runtime 選択** — `CaptureCodeCompatHolder.context` (process-scoped lazy) が `ServiceLoader<CompatContext.Factory>` を走らせ、 `minVersion <= currentVersion` を満たす Factory の中で最大の `minVersion` を選ぶ。 `dev` / `Beta` / `RC` の track 区別も `KotlinToolingVersion` resolver が担当

新しい Kotlin バージョンへの対応手順は [`compat/README.md`](compat/README.md) の "Adding Support for New Kotlin Versions" を参照。 `generate-compat-module.sh` でスケルトン生成できる。

## Build / dependency 配線

- `build.gradle.kts` 主要設定:
    - `compileOnly(libs.kotlin.compiler.embeddable.k200)` — main は 2.0.0 baseline 固定
    - `compileOnly(project(":compiler-plugin:compat"))` + `bundled(project(":compiler-plugin:compat"))` — compat を POM に流出させず shadowJar に同梱
    - `bundled(project(":compiler-plugin:compat-k{200,202,210,220,230,240rc}"))` — 全 compat-kXXX を shadow JAR で同梱。 新バージョン追加時はここに足す
    - `mainClassesOnly` / `mainRuntimeClassesOnly` outgoing variant — task-118 / task-122 で導入。 compat-kXXX 側はこれを `compileOnly` / `testRuntimeOnly` で引いて main 側 domain SSoT (`<Logic>Errors.kt` / `feature/`) を参照
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
