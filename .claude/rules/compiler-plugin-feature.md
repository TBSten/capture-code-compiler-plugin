---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/feature/*/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/.../feature/<feature>/` 直下) には **その feature 内の複数 logic (FIR checker / IR transformer / 各 compat-kXXX 実装) が共有する SSoT 定数・型・薄い bridge** を flat に配置する。 task-118 で feature/ 配下の domain SSoT は `:compiler-plugin:compat` から **main module** に引き上げられ (`mainClassesOnly` outgoing configuration 経由で各 `compat-kXXX/` から compileOnly 参照可能になった)、 plugin 横断 SSoT としての立場を明示化した。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# 現状の feature ディレクトリ

| feature | 内容 |
| --- | --- |
| `markerDefinition/` | `@CaptureCode` メタ annotation の domain (Logic A / F)。 配下に `fir/` / `ir/` の logic sub-tree を抱える |
| `capturedSources/` | `capturedSources<T>()` 経由の source capture (Logic B / D / G / H)。 配下に `fir/` / `ir/` の logic sub-tree を抱える。 旧 historical naming の `capturedsources/` (CallableId SSoT) は task-118 で `capturedSources/` 配下に統合済 |

> 注: feature 命名は **lowerCamelCase** に統一済 (task-118)。 旧 `captured_sources` / `captured_expression` の snake_case naming は廃止。

# 置いてよいもの

その feature の **複数 logic が参照する** SSoT:

- **FQN / ClassId / CallableId SSoT** (`CaptureCodeCallableIds.kt`, `CaptureCodeFqns.kt` 相当)
- **検証用 regex SSoT** (FIR Checker と IR const-fold 双方が参照する場合)
- **plugin 固有 option の参照点** (per-marker options 等、 `CaptureCodeMarkerOptions` のような data class)
- **データクラス** (`CapturedSite`, marker option 等)
- **bridge / extractor** — 共通入出力を担う薄い層 (例: `feature/capturedSources/ir/normalize/...` 直下の薄い orchestrator)

# feature 命名

- ユーザ目線で機能内容が予想できる名前 (例: `markerDefinition`, `capturedSources`)
- パッケージ命名は **lowerCamelCase** に統一 (task-118 で確定)
- 動詞句は避ける (logic と区別するため)

# 置いてはいけないもの

- **logic の処理本体 (FIR checker class / IR rewriter / filler / userargs)** — 一段下の `feature/<feature>/<phase>/<logic>/` 配下に置く。 task-120-B Phase 3-5 で IR logic 本体は main 側に集約済 (`feature/capturedSources/ir/collectDeclarationSite/`, `feature/capturedSources/ir/rewriteCapturedSourcesCall/buildMarkerInstance/{filler,userargs}/` 等)。 FIR Checker class のみ `compat-kXXX/.../checker/` のバージョン依存層に残る (Visitor base class drift があるため)
- **1 つの logic にしか使われない定数** — その logic ディレクトリ配下に置く (後で複数 logic が参照するようになった時点で feature 直下に引き上げる)
- **plugin 横断 (= 複数 feature が参照する) 定数** — feature 横断 SSoT は基本的に存在しないが、 もし発生したら `compiler-plugin/src/main/.../code/` (root) に上げる
- **compiler API ラッパー** (domain 非依存) — 将来 `compiler-plugin/src/main/.../utils/` に置く
- **plugin 横断 Error / Warning 基盤** — `compiler-plugin/src/main/.../error/` または `compiler-plugin/src/main/.../warning/` 配下
- **diagnostic 文面 SSoT (`*Errors.kt`)** — feature 直下ではなく **その logic 直下** (例: `feature/markerDefinition/fir/validateMarkerAnnotation/MarkerAnnotationErrors.kt`) に置く。 文面は logic に紐づくため

# Good 例

```kotlin
// compiler-plugin/src/main/.../feature/capturedSources/CaptureCodeCallableIds.kt
public object CaptureCodeCallableIds {
    public val packageFqName: FqName = FqName("me.tbsten.capture.code")
    public val capturedSourcesName: Name = Name.identifier("capturedSources")
    public val capturedSources: CallableId = CallableId(packageFqName, capturedSourcesName)
}
```

FIR checker (`K{XXX}CapturedSourcesCallChecker`) と IR transformer (`K{XXX}CapturedSourcesRewriter`) の双方から参照される。 一方だけが知っている状態にすると「checker は走るが書き換えは走らない / 逆」 というバグの温床になる。

```kotlin
// compiler-plugin/src/main/.../feature/capturedSources/ir/normalize/NormalizeOptions.kt
public data class NormalizeOptions(
    val includeKdoc: Boolean,
    val includeImports: Boolean,
    val includeAnnotationLines: Boolean,
    val dedent: Boolean,
)
```

各 `compat-kXXX/` の Source extractor が `CaptureCodePluginConfig` → `NormalizeOptions` 経由で normalize chain に渡す。

# Bad 例

```kotlin
// compiler-plugin/compat-k200/.../K200CallableIds.kt ← NG
internal val CapturedSourcesCallableId = CallableId(
    FqName("me.tbsten.capture.code"),
    Name.identifier("capturedSources"),
)
```

→ `feature/capturedSources/CaptureCodeCallableIds.kt` を参照する。 各 compat-kXXX で重複定義しない。

```kotlin
// compiler-plugin/src/main/.../feature/capturedSources/CapturedSourcesRewriterImpl.kt ← NG
class CapturedSourcesRewriterImpl { /* IR 置換処理 */ }
```

→ IR 置換は task-120-B Phase 3-4 で main 側に集約済 (`feature/capturedSources/ir/rewriteCapturedSourcesCall/RewriteCapturedSourcesCall.kt`)。 feature 直下は「複数 logic が共有する SSoT」だけで、 logic class 本体は **一段下の `feature/<feature>/<phase>/<logic>/` 配下** に置く。 IR drift D5–D8 は `CompatContext` SPI の 11 IR primitive method (`createIrCall`, `putValueArgument`, `setTypeArgument` 等) で吸収される。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
