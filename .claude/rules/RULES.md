# Compiler Plugin Architecture Rules

このディレクトリ (`.claude/rules/`) には、 Capture Code Kotlin Compiler Plugin プロジェクトの **モジュール構成と責務分離** に関するルールを集めています。

本プロジェクトは runtime drift (FIR checker signature 等の Kotlin minor バージョン間 drift) を回避する都合で、 **エントリポイントだけが `compiler-plugin/src/main/` に置かれ、 plugin の実装本体 (feature / fir / error / 共有 SPI) は `compiler-plugin/compat/` モジュールに集約** されている点が一般的な Kotlin Compiler Plugin プロジェクトと異なります。 各ルールの `paths` glob はこの構造に合わせて、 `compiler-plugin/src/main/...` と `compiler-plugin/compat/src/main/...` の両方をスコープにしています。

## ルール 7 本の関係

| ルールファイル | 対象 path | スコープ |
| --- | --- | --- |
| `compiler-plugin-top.md` | `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/*.kt` (直下のみ) | plugin の組み立て役。 `CaptureCodeCompilerPluginRegistrar` 等 4 ファイルだけが置ける |
| `compiler-plugin-compat.md` | `compiler-plugin/compat/src/main/kotlin/**/compat/**`, `compiler-plugin/compat-k*/src/**` | Kotlin バージョン差吸収 SPI + 各 `compat-kXXX` impl。 業務ロジックは持ち込まない |
| `compiler-plugin-error.md` | `compiler-plugin/compat/src/main/kotlin/**/error/**` | plugin 横断 Error / Diagnostic 基盤 |
| `compiler-plugin-warning.md` | `compiler-plugin/compat/src/main/kotlin/**/warning/**` | plugin 横断 Warning 基盤 (現状未整備、 将来追加用) |
| `compiler-plugin-utils.md` | `compiler-plugin/compat/src/main/kotlin/**/utils/**` | domain 非依存の compiler API ヘルパ (現状未整備、 将来追加用) |
| `compiler-plugin-feature.md` | `compiler-plugin/compat/src/main/kotlin/**/feature/*/*.kt` (feature 直下) | feature 内の複数 logic が共有する SSoT |
| `compiler-plugin-feature-logic.md` | `compiler-plugin/compat/src/main/kotlin/**/feature/*/*/**`, `compiler-plugin/compat-k*/src/main/kotlin/**/{checker,filler,userargs}/**` | logic に閉じた処理本体 |

`paths` の glob で「直下のみ」(`feature/*/*.kt`) と「再帰」(`feature/*/*/**`) を区別しているので、 同じ feature ツリーでも階層ごとに別ルールが適用されます。

## 参考

- `compiler-plugin/README.md` — compiler-plugin module の構造解説 + How It Works
- `compiler-plugin/compat/README.md` — compat layer の SPI 解説 + 新 Kotlin バージョン追加手順
- `docs/architecture.md` — プロジェクト全体のアーキテクチャ俯瞰
