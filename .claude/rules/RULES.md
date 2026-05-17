# Compiler Plugin Architecture Rules

このディレクトリ (`.claude/rules/`) には、 Capture Code Kotlin Compiler Plugin プロジェクトの **モジュール構成と責務分離** に関するルールを集めています。

本プロジェクトは runtime drift (FIR checker signature 等の Kotlin minor バージョン間 drift) を回避する都合で、 **main module (`compiler-plugin/src/main/`) を Kotlin 2.0.0 baseline で固定 compile し、 バージョン依存の薄い adapter (`CompatContextImpl` + `K{XXX}IrVisitors` + FIR Checker) のみ `compiler-plugin/compat-kXXX/` モジュールに分離** している点が一般的な Kotlin Compiler Plugin プロジェクトと異なります。

task-118 以降、 **domain SSoT (feature/ 配下の CallableId / NormalizeOptions / `*Errors.kt` 等)** は `compiler-plugin/src/main/...` (main module) に集約されており、 各 `compat-kXXX/` からは `mainClassesOnly` outgoing configuration 経由で compileOnly 参照しています。 task-121 で error/ + warning/ 骨格も main 側に新設、 task-122 で diagnostic 文面は **English-only** に統一されました (旧 `BilingualMessage` / `CAPTURECODE_LOCALE` は撤去済)。

**task-120-B Phase 1-7 (`0.2.0`) で IR phase logic 本体 (`CollectDeclarationSite` / `RewriteCapturedSourcesCall` / `BuildMarkerInstance` / 3 filler / 2 userargs / `WarnIfNoMarkerFound`) も main 側に集約済**。 IR drift D5-D8 は `CompatContext` SPI の 11 IR primitive method (Phase 2 で追加: `acceptIrVisitor` / `walkIrFileDeclarations` / `loadFileText` / `putValueArgument` / `createIrCall` / `setTypeArgument` / `valueParametersOf` / `irExpressionBodyOf` / `irConstString` / `irGetEnumValueOf` / `irGetClassReferenceOf`) で吸収。 各 compat-kXXX は **3-4 .kt + 0-5 .java** の slim 構造に縮小しました。 holder 群 (`CaptureCodeCompatHolder` / `CaptureCodePluginConfigHolder` / `CaptureCodeMessageCollectorHolder`) と `CaptureCodePluginConfig` data class も main 側に移動 (Phase 1)。

各ルールの `paths` glob はこの構造に合わせて、 `compiler-plugin/src/main/...` (main) と `compiler-plugin/compat/src/main/...` (SPI 2 ファイル) / `compiler-plugin/compat-k*/...` (各 compat impl) のスコープを分けています。

## ルール 7 本の関係

| ルールファイル | 対象 path | スコープ |
| --- | --- | --- |
| `compiler-plugin-top.md` | `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/*.kt` (直下のみ) | plugin の組み立て役。 `CaptureCodeCompilerPluginRegistrar` 等 5 ファイルだけが置ける (task-120-B Phase 1 で `CaptureCodePluginConfig` も main 側に追加) |
| `compiler-plugin-compat.md` | `compiler-plugin/compat/src/main/kotlin/**/compat/**`, `compiler-plugin/compat-k*/src/**` | Kotlin バージョン差吸収 SPI 2 ファイル + 各 `compat-kXXX` impl。 業務ロジックは持ち込まない (task-118 以降 domain SSoT は main 側、 task-120-B Phase 3-5 で IR logic も main 側) |
| `compiler-plugin-error.md` | `compiler-plugin/src/main/kotlin/**/error/**` (main) | plugin 横断 Error / Diagnostic 基盤。 task-121 で main 側に SPI 骨格新設、 task-122 で文面は English-only に統一 |
| `compiler-plugin-warning.md` | `compiler-plugin/src/main/kotlin/**/warning/**` (main) | plugin 横断 Warning 基盤。 task-121 で main 側に骨格新設、 task-123/127/128 + task-120-B Phase 7 で具体実装 |
| `compiler-plugin-utils.md` | `compiler-plugin/src/main/kotlin/**/utils/**` | domain 非依存の compiler API ヘルパ (現状未整備、 将来追加用) |
| `compiler-plugin-feature.md` | `compiler-plugin/src/main/kotlin/**/feature/*/*.kt` (feature 直下、 main 側) | feature 内の複数 logic が共有する SSoT |
| `compiler-plugin-feature-logic.md` | `compiler-plugin/src/main/kotlin/**/feature/*/*/**` (main), `compiler-plugin/compat-k*/src/main/kotlin/**/checker/**` | logic に閉じた処理本体 + diagnostic 文面 SSoT (`<Logic>Errors.kt`)。 IR logic は main 側、 FIR Checker のみ compat-kXXX 側 (task-120-B Phase 6 で `compat-k*/filler` / `compat-k*/userargs` は削除済) |

`paths` の glob で「直下のみ」(`feature/*/*.kt`) と「再帰」(`feature/*/*/**`) を区別しているので、 同じ feature ツリーでも階層ごとに別ルールが適用されます。

## 参考

- `compiler-plugin/README.md` — compiler-plugin module の構造解説 + How It Works (main 側 IR chain 図)
- `compiler-plugin/compat/README.md` — compat layer の SPI 解説 (23 method) + 新 Kotlin バージョン追加手順
- `docs/architecture.md` — プロジェクト全体のアーキテクチャ俯瞰 + task-120-B Phase 1-7 経緯
