---
paths:
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/error/**/*.kt"
---

このディレクトリ (`compiler-plugin/compat/.../error/`) には **plugin 全体で使う構造化 Error / Diagnostic 基盤** を配置する。 `:compiler-plugin:compat` モジュールに置くことで、 main module からも全 `compat-kXXX/` impl からも参照できる SSoT になっている (各 `compat-kXXX/` の diagnostic factory が共通文面を参照する構造)。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# Error / Diagnostic 基盤

現状置かれているファイル:

- `CaptureCodeDiagnosticMessages.kt` — plugin が発する全診断メッセージの **bilingual (英語 + 日本語) SSoT**。 各 compat-kXXX layer の diagnostic factory (`K200CaptureCodeDiagnostics` 等) の renderer がここを参照する
- `CaptureCodeDiagnosticLocale.kt` — `CaptureCodeMessageLocale` (`EN` / `JA`) と現在の locale resolver
- `CaptureCodeFillerClassIds.kt` — diagnostic 経由でユーザに見せる runtime filler 型の ClassId / FQN SSoT

将来追加する場合の想定 (template 由来、 必要になったら導入):

- `CaptureCodeCompilerPluginError.kt` — `interface` marker (全 Error 実装の親)
- `ErrorContextDsl.kt` — Error メッセージ組み立て DSL
- `Errors.kt` — 複数 feature から throw されうる Error 実装
- `Replies.kt` — ユーザへの提案文 (「こうすれば直る」テンプレ) の SSoT (現状は `CaptureCodeDiagnosticMessages` 内の `Suggested fix:` / `修正方法:` ブロックが担う)
- `ReportError.kt` / `ReportErrors.kt` — `MessageCollector.report(error, location)` 拡張
- `ThrowAsException.kt` — `SomeError(args).throwAsException()` 拡張 (defensive guard 用)

# 必須の使い方

- **メッセージ文面は `CaptureCodeDiagnosticMessages` の `BilingualMessage` を経由する** — `K{XXX}CaptureCodeDiagnostics` の renderer が直接英文を書かないように
- 既存 test は **文面の部分一致** (`shouldContain`) で検証しているため、 英語側の既存 phrase (`must be 'internal' or 'private'` 等) は **後方互換のため温存**
- 新規 diagnostic を追加するときは
    1. `CaptureCodeDiagnosticMessages` に `BilingualMessage` を追加
    2. `K{XXX}CaptureCodeDiagnostics` (各 compat-kXXX) に factory + renderer を追加
    3. checker (`K{XXX}*Checker`) から `reporter.reportOn(...)` を呼ぶ
- メッセージ ID 命名規則: `CC_<feature>_<rule>` (例: `CC_MARKER_VISIBILITY`, `CC_CAPTUREDSOURCES_T_NOT_INTERFACE`)

# 置いてはいけないもの

- **`KtDiagnosticFactory*` の宣言** — `compat-kXXX/.../checker/K{XXX}CaptureCodeDiagnostics.kt` 配下。 factory は `KtDiagnosticsContainer` と renderer chain が密結合のため共有不可
- **特定 feature / logic にしか出ない Diagnostic の renderer** — 共有 message ID なら `CaptureCodeDiagnosticMessages` に追加、 そうでなければ `compat-kXXX/.../checker/` 配下に閉じる
- **Warning 実装** — `compat/.../warning/` (現状未整備) に置く想定

# Good 例

```kotlin
// compiler-plugin/compat/.../error/CaptureCodeDiagnosticMessages.kt
public object CaptureCodeDiagnosticMessages {
    public val CC_MARKER_VISIBILITY: BilingualMessage = BilingualMessage(
        en = "Marker annotation ''{0}'' must be 'internal' or 'private'.\n" +
            "Suggested fix: change the visibility of {0}.",
        ja = "marker annotation ''{0}'' は 'internal' か 'private' である必要があります。\n" +
            "修正方法: {0} の可視性を変更してください。",
    )
}

// compiler-plugin/compat-k200/.../checker/K200CaptureCodeDiagnostics.kt
internal object K200CaptureCodeDiagnostics : KtDiagnosticsContainer() {
    val CC_MARKER_VISIBILITY by error1<KtElement, FqName>()
    // renderer は CaptureCodeDiagnosticMessages.CC_MARKER_VISIBILITY を locale 解決して描画
}
```

# Bad 例

```kotlin
// compat-k200/.../checker/K200CaptureCodeDiagnostics.kt の中で英文を直書き ← NG
val CC_MARKER_VISIBILITY = "Marker annotation '${'$'}fqn' must be 'internal' or 'private'."
```

→ 文面は `compiler-plugin/compat/.../error/CaptureCodeDiagnosticMessages.kt` の `BilingualMessage` に集約する。 各 `K{XXX}` の renderer は ID 参照のみ。

```kotlin
// compiler-plugin/compat/.../error/CaptureCodeMarkerVisibilityError.kt ← NG
// feature/logic 固有の Error class を error/ に追加
class MarkerVisibilityError(val fqn: FqName) : RuntimeException()
```

→ FIR Checker 経由で出す診断は `KtDiagnosticFactory*` の責務で、 plugin 横断 Error interface には乗らない。 `compat-kXXX/.../checker/` 配下の `K{XXX}CaptureCodeDiagnostics` に追加する。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
