---
paths:
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/feature/*/*.kt"
---

このディレクトリ (`compiler-plugin/compat/.../feature/<feature>/` 直下) には **その feature 内の複数 logic (FIR checker / IR transformer / 各 compat-kXXX 実装) が共有する SSoT 定数・型** を flat に配置する。 `:compiler-plugin:compat` モジュールに置くことで、 main module からも全 `compat-kXXX/` impl からも参照できる。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# 現状の feature ディレクトリ

| feature | 内容 |
| --- | --- |
| `captured_sources/` | `capturedSources<T>()` 経由の source capture (Logic B / D / G / H)。 配下に source normalize の helper を抱える |
| `capturedsources/` (underscore なし) | `capturedSources<T>()` callable の identity SSoT 専用 (`CaptureCodeCallableIds.kt`)。 historical naming だが test / IR / FIR から `CallableId` 経由で参照 |
| `captured_expression/` | `@Marker(expr)` 式 annotation の collection (Logic B-fir / Logic E)。 主に test 配下に現れる |

> 注: `captured_sources` / `capturedsources` という 2 つの命名が混在しているのは historical artifact (Logic G の SSoT を後発で追加した名残)。 新規 feature を追加する場合は **lowerCamelCase 単一語 or snake_case** どちらかに揃えて、 既存ファイルへの破壊的 rename は別 ticket で扱う。

# 置いてよいもの

その feature の **複数 logic が参照する** SSoT:

- **FQN / ClassId / CallableId SSoT** (`CaptureCodeCallableIds.kt`, `CaptureCodeFqns.kt` 相当)
- **検証用 regex SSoT** (FIR Checker と IR const-fold 双方が参照する場合)
- **plugin 固有 option の参照点** (per-marker options 等、 `CaptureCodeMarkerOptions` のような data class)
- **データクラス** (`CapturedSite`, marker option 等)
- **bridge / extractor** — 共通入出力を担う薄い層 (例: `feature/captured_sources/normalize/CaptureCodePluginConfigBridge.kt`)

# feature 命名

- ユーザ目線で機能内容が予想できる名前 (例: `captured_sources`, `captured_expression`)
- パッケージ命名は既存に揃える (`feature/<featureName>/` の lowerCamelCase or snake_case)
- 動詞句は避ける (logic と区別するため)

# 置いてはいけないもの

- **logic の処理本体 (FIR checker class / IR transformer class)** — `compat-kXXX/.../checker/` や `compat-kXXX/.../K{XXX}IrTransform.kt` 等の **バージョン依存層** に置く
- **1 つの logic にしか使われない定数** — その logic ディレクトリ配下に置く (後で複数 logic が参照するようになった時点で feature 直下に引き上げる)
- **plugin 横断 (= 複数 feature が参照する) 定数** — feature 横断 SSoT は基本的に存在しないが、 もし発生したら `compat/.../compat/` (`CaptureCodeMarkerOptions` 等) に上げる
- **compiler API ラッパー** (domain 非依存) — 将来 `compat/.../utils/` に置く
- **plugin 横断 Error / Warning** — `compat/.../error/` / `compat/.../warning/`

# Good 例

```kotlin
// compiler-plugin/compat/.../feature/capturedsources/CaptureCodeCallableIds.kt
public object CaptureCodeCallableIds {
    public val packageFqName: FqName = FqName("me.tbsten.capture.code")
    public val capturedSourcesName: Name = Name.identifier("capturedSources")
    public val capturedSources: CallableId = CallableId(packageFqName, capturedSourcesName)
}
```

FIR checker (`K200CapturedSourcesCallChecker`) と IR transformer (`K200CapturedSourcesRewriter`) の双方から参照される。 一方だけが知っている状態にすると「checker は走るが書き換えは走らない / 逆」というバグの温床になる。

```kotlin
// compiler-plugin/compat/.../feature/captured_sources/normalize/NormalizeOptions.kt
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

→ `feature/capturedsources/CaptureCodeCallableIds.kt` を参照する。 各 compat-kXXX で重複定義しない。

```kotlin
// compiler-plugin/compat/.../feature/captured_sources/CapturedSourcesRewriterImpl.kt ← NG
class CapturedSourcesRewriterImpl { /* IR 置換処理 */ }
```

→ IR 置換は version-sensitive (IR drift D5–D8) なので **必ず `compat-kXXX/.../K{XXX}CapturedSourcesRewriter.kt`** に置く。 feature 直下は「複数 logic が共有する SSoT」だけ。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
