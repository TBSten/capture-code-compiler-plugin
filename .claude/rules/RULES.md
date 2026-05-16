# Compiler Plugin Architecture Rules

このディレクトリ (`.claude/rules/`) には、 Capture Code Kotlin Compiler Plugin プロジェクトの **モジュール構成と責務分離** に関するルールを集めています。

本プロジェクトは runtime drift (FIR checker signature 等の Kotlin minor バージョン間 drift) を回避する都合で、 **main module (`compiler-plugin/src/main/`) を Kotlin 2.0.0 baseline で固定 compile し、 バージョン依存の業務ロジック (FIR Checker / IR Transform / KtDiagnosticFactory* など) は `compiler-plugin/compat-kXXX/` モジュールに分離** している点が一般的な Kotlin Compiler Plugin プロジェクトと異なります。

task-118 以降、 **domain SSoT (feature/ 配下の CallableId / NormalizeOptions / `*Errors.kt` 等)** は `compiler-plugin/src/main/...` (main module) に集約されており、 各 `compat-kXXX/` からは `mainClassesOnly` outgoing configuration 経由で compileOnly 参照しています。 task-121 で error/ + warning/ 骨格も main 側に新設、 task-122 で diagnostic 文面は **English-only** に統一されました (旧 `BilingualMessage` / `CAPTURECODE_LOCALE` は撤去済)。

各ルールの `paths` glob はこの構造に合わせて、 `compiler-plugin/src/main/...` (main) と `compiler-plugin/compat/src/main/...` / `compiler-plugin/compat-k*/...` (compat) の両方をスコープにしています。

## ルール 7 本の関係

| ルールファイル | 対象 path | スコープ |
| --- | --- | --- |
| `compiler-plugin-top.md` | `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/*.kt` (直下のみ) | plugin の組み立て役。 `CaptureCodeCompilerPluginRegistrar` 等 4 ファイルだけが置ける |
| `compiler-plugin-compat.md` | `compiler-plugin/compat/src/main/kotlin/**/compat/**`, `compiler-plugin/compat-k*/src/**` | Kotlin バージョン差吸収 SPI + 各 `compat-kXXX` impl。 業務ロジックは持ち込まない (task-118 以降 domain SSoT は main 側) |
| `compiler-plugin-error.md` | `compiler-plugin/src/main/kotlin/**/error/**` (main), `compiler-plugin/compat/src/main/kotlin/**/error/**` (compat 共有) | plugin 横断 Error / Diagnostic 基盤。 task-121 で main 側に SPI 骨格新設、 task-122 で文面は English-only に統一 |
| `compiler-plugin-warning.md` | `compiler-plugin/src/main/kotlin/**/warning/**` (main), `compiler-plugin/compat/src/main/kotlin/**/warning/**` (compat 共有) | plugin 横断 Warning 基盤。 task-121 で main 側に骨格新設、 具体実装は task-123 以降 |
| `compiler-plugin-utils.md` | `compiler-plugin/compat/src/main/kotlin/**/utils/**` | domain 非依存の compiler API ヘルパ (現状未整備、 将来追加用) |
| `compiler-plugin-feature.md` | `compiler-plugin/src/main/kotlin/**/feature/*/*.kt` (feature 直下、 main 側) | feature 内の複数 logic が共有する SSoT |
| `compiler-plugin-feature-logic.md` | `compiler-plugin/src/main/kotlin/**/feature/*/*/**` (main), `compiler-plugin/compat-k*/src/main/kotlin/**/{checker,filler,userargs}/**` | logic に閉じた処理本体 + diagnostic 文面 SSoT (`<Logic>Errors.kt`) |

`paths` の glob で「直下のみ」(`feature/*/*.kt`) と「再帰」(`feature/*/*/**`) を区別しているので、 同じ feature ツリーでも階層ごとに別ルールが適用されます。

## 参考

- `compiler-plugin/README.md` — compiler-plugin module の構造解説 + How It Works
- `compiler-plugin/compat/README.md` — compat layer の SPI 解説 + 新 Kotlin バージョン追加手順
- `docs/architecture.md` — プロジェクト全体のアーキテクチャ俯瞰
