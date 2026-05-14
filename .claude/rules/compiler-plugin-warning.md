---
paths:
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/warning/**/*.kt"
---

このディレクトリ (`compiler-plugin/compat/.../warning/`) は **plugin 全体で使う構造化 Warning 基盤の置き場所** として予約されている。 `compat/.../error/` と完全に対称な構造で、 `:compiler-plugin:compat` モジュール配下に置くことで全 `compat-kXXX/` impl から参照できる SSoT になる。

**現状このディレクトリ自体まだ存在しない** — 警告系の diagnostic は今のところ `compat-kXXX/.../checker/K{XXX}CaptureCodeDiagnostics.kt` の `warning*` factory + `CaptureCodeDiagnosticMessages` の文面で表現されているため、 単独の `warning/` ディレクトリ整備は必要になった時点で行う。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照** すること。

# 将来追加する場合の Warning 基盤

- `CaptureCodeCompilerPluginWarning.kt` — `interface` marker
- `WarningContextDsl.kt` — Warning メッセージ組み立て DSL
- `Warnings.kt` — plugin 横断 Warning 実装
- `ReportWarning.kt` / `ReportWarnings.kt` — `MessageCollector.report(warning, location)` 拡張

# 必須の使い方 (整備後)

- **生 `messageCollector.report(SEVERITY.WARNING, "...", location)` 禁止** → 必ず `Warning` 実装経由
- 各 Warning 実装は `error/` と同じく `message` / `description` / `context` / `replies` を override
- 警告は **ユーザが対処できる情報** (どのファイル / どの宣言 / 何を直せば消えるか) を必ず含む
- bilingual 文面は `CaptureCodeDiagnosticMessages` と統合し、 `BilingualMessage` を再利用する

# 置いてはいけないもの

- **特定 feature / logic にしか出ない Warning** — checker 由来なら `compat-kXXX/.../checker/` 配下、 IR 由来なら `compat-kXXX/.../K{XXX}IrTransform.kt` 経由で `MessageCollector` に投げる
- **Error 実装** — `compat/.../error/` に置く

# Good 例 (将来)

```kotlin
// compiler-plugin/compat/.../warning/Warnings.kt
public data class CaptureCodeIncludeImportsNoOpWarning(
    val markerFqn: FqName,
) : CaptureCodeCompilerPluginWarning {
    override val message = "includeImports = true is set for ''{0}'' but the target is not a file-level capture; option is ignored."
    override val replies = listOf(Replies.useFileLevelCapture)
}

messageCollector.report(CaptureCodeIncludeImportsNoOpWarning(markerFqn), location)
```

# Bad 例

```kotlin
messageCollector.report(CompilerMessageSeverity.WARNING, "includeImports is ignored", loc)  // ← NG
```

→ `warning/Warnings.kt` に実装を追加する (or 既存の `compat-kXXX/.../checker/` で warning factory として実装する)。

---

参考:

- `compiler-plugin/README.md`
- `.claude/rules/compiler-plugin-error.md`
