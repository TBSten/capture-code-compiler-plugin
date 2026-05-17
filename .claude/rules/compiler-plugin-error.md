---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/error/**/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/.../error/`) には **plugin 全体で使う構造化 Error / Diagnostic 基盤** を配置する。

task-121 以降は **main module 側 (`compiler-plugin/src/main/kotlin/.../error/`)** が SPI 本体 (`CaptureCodeCompilerPluginError` interface, DSL, `ReportError`, ...) の SSoT。 compat layer 側にあった補助 SSoT (`CaptureCodeFillerClassIds.kt` 等) も既に main module の `feature/markerDefinition/` 配下に移動済。 task-122 で `BilingualMessage` / `CaptureCodeDiagnosticLocale` (i18n 機構) は撤去済で、 diagnostic 文面 SSoT は feature ローカル (`feature/<feature>/.../<Logic>Errors.kt`) に **English-only** で集約されている。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# Error / Diagnostic 基盤

現状置かれているファイル:

## main module (`compiler-plugin/src/main/kotlin/.../error/`)

- `CaptureCodeCompilerPluginError.kt` — `interface` (全 Error 実装の親)。 `id` / `message` (English-only) / `reply` の 3 フィールド SSoT
- `ErrorContextDsl.kt` — ad-hoc な Error を組み立てる `errorContext("CC_XXX") { ... }` DSL
- `Errors.kt` — 複数 feature から throw されうる cross-cutting Error 用 SSoT (現状空)
- `Replies.kt` — ユーザへの提案文 (`Suggested fix:` テンプレ) 共有 SSoT (現状空)
- `ReportError.kt` — `DiagnosticReporter.reportError(error, source, context, compat, [arg])` 拡張 (`KtDiagnosticFactory0` / `KtDiagnosticFactory1` 両対応)

## feature 配下 (`compiler-plugin/src/main/.../feature/<feature>/`)

- `CaptureCodeFillerClassIds.kt` (`feature/markerDefinition/` 直下) — diagnostic 経由でユーザに見せる runtime filler 型の ClassId / FQN SSoT。 各 `compat-kXXX` から `mainClassesOnly` outgoing 経由で compileOnly 参照される

## feature 配下 (`compiler-plugin/src/main/.../feature/<feature>/.../<Logic>Errors.kt`)

- `MarkerAnnotationErrors.kt` (Logic F) / `CapturedSourcesCallErrors.kt` (Logic G) など — diagnostic 文面の SSoT。 各 `compat-kXXX` の `CompatContextImpl.kt` nested diagnostics object の renderer がこの `.message` を参照する

将来追加する場合の想定:

- `ThrowAsException.kt` — `SomeError(args).throwAsException()` 拡張 (defensive guard 用)
- `ReportErrors.kt` — 同時に複数 Error を report するヘルパ

# 必須の使い方

- **メッセージ文面は feature ローカルの `*Errors.kt` (`CaptureCodeCompilerPluginError.message`) を経由する** — `K{XXX}Diagnostics` (各 `compat-kXXX/CompatContextImpl.kt` nested object) の renderer は文字列直書きせず `.message` を参照する
- 既存 checker test は **文面の部分一致** (`shouldContain`) で検証しているため、 既存 phrase (`has an unsupported type` / `cannot be declared as 'expect'` 等) は **後方互換のため温存**
- 新規 diagnostic を追加するときは
    1. 対応 feature の `*Errors.kt` (例: `feature/markerDefinition/fir/validateMarkerAnnotation/MarkerAnnotationErrors.kt`) に `CaptureCodeCompilerPluginError` 実装を追加 (`id` + English `message` + optional `reply`)
    2. 全 `compat-kXXX/.../CompatContextImpl.kt` の nested `K{XXX}Diagnostics` に `KtDiagnosticFactory*` を追加し、 `MAP` (id → factory) と `K{XXX}CaptureCodeDefaultMessages` (factory → message) の両方に entry を追加
    3. checker (`K{XXX}*Checker` / 共通 `Validate*.kt`) から `reporter.reportError(SomeErrors.SOME, source, context, compat[, arg])` を呼ぶ
- メッセージ ID 命名規則: `CC_<feature>_<rule>` (例: `CC_MARKER_IS_EXPECT`, `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE`)

# 置いてはいけないもの

- **`KtDiagnosticFactory*` の宣言** — `compat-kXXX/.../CompatContextImpl.kt` の nested `K{XXX}Diagnostics` 配下。 factory は `KtDiagnosticsContainer` と renderer chain が密結合のため共有不可
- **特定 feature / logic にしか出ない Diagnostic の文面を `error/` 配下に追加** — 文面 SSoT は feature ローカル (`feature/<feature>/.../<Logic>Errors.kt`) に閉じる
- **`BilingualMessage` / `CaptureCodeMessageLocale` の復活** — task-122 で撤去済。 i18n が必要になった場合は別 task で API design からやり直す
- **Warning 実装** — `compiler-plugin/src/main/.../warning/` に置く (task-121 で main 側に新設、 task-123/127/128 + task-120-B Phase 7 で具体実装)

# Good 例

```kotlin
// compiler-plugin/src/main/.../feature/markerDefinition/fir/validateMarkerAnnotation/MarkerAnnotationErrors.kt
public object MarkerAnnotationErrors {
    public val IS_EXPECT: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_IS_EXPECT"
        override val message: String =
            "@CaptureCode marker annotation cannot be declared as 'expect'. " +
                "Markers must be concrete annotation declarations (see design §7.6).\n" +
                "Suggested fix: remove the 'expect' modifier; declare the marker concretely in commonMain."
        override val reply: String? =
            "Remove the 'expect' modifier; declare the marker concretely in commonMain."
    }
}

// compiler-plugin/compat-k200/.../CompatContextImpl.kt の nested K200Diagnostics
public object K200Diagnostics {
    public val CC_MARKER_IS_EXPECT: KtDiagnosticFactory0 by error0<PsiElement>()

    private object K200CaptureCodeDefaultMessages : BaseDiagnosticRendererFactory() {
        override val MAP: KtDiagnosticFactoryToRendererMap =
            KtDiagnosticFactoryToRendererMap("CaptureCode").apply {
                put(CC_MARKER_IS_EXPECT, MarkerAnnotationErrors.IS_EXPECT.message)
            }
    }
}
```

# Bad 例

```kotlin
// compat-k200/.../CompatContextImpl.kt の K200CaptureCodeDefaultMessages 内で英文を直書き ← NG
put(CC_MARKER_IS_EXPECT, "Marker annotation cannot be declared as 'expect'.")
```

→ 文面は `feature/<feature>/.../<Logic>Errors.kt` の `CaptureCodeCompilerPluginError.message` に集約する。 各 `K{XXX}` の renderer は `.message` 参照のみ。

```kotlin
// compiler-plugin/src/main/.../error/CaptureCodeMarkerVisibilityError.kt ← NG
// feature/logic 固有の Error class を error/ 配下に追加
class MarkerVisibilityError(val fqn: FqName) : RuntimeException()
```

→ FIR Checker 経由で出す診断は `KtDiagnosticFactory*` の責務で、 plugin 横断 Error interface には乗らない。 文面は feature ローカル `*Errors.kt`, factory は `compat-kXXX/.../CompatContextImpl.kt` の nested object に追加する。

```kotlin
// compiler-plugin/src/main/.../error/CaptureCodeDiagnosticMessages.kt ← NG (task-122 で撤去済)
public object CaptureCodeDiagnosticMessages {
    public val MARKER_IS_EXPECT: BilingualMessage = BilingualMessage(en = ..., ja = ...)
}
```

→ task-122 で i18n 機構は撤去済。 文面は feature ローカル `*Errors.kt` の `.message` フィールドに **English-only** で書く。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
