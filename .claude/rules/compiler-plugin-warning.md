---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/warning/**/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/.../warning/`) は **plugin 全体で使う構造化 Warning 基盤の置き場所**。 task-121 以降は **main module 側** (`compiler-plugin/src/main/kotlin/.../warning/`) が SPI 本体 (`CaptureCodeCompilerPluginWarning` interface, DSL, `ReportWarning`, ...) の SSoT。 `error/` と完全に対称な構造で、 各 `compat-kXXX/` impl からは `mainClassesOnly` outgoing 経由で compileOnly 参照する。

task-122 で i18n 機構 (`BilingualMessage` / `CaptureCodeMessageLocale`) は撤去済。 warning 文面も `error/` と同様に **English-only** で書く。

**現状 4 つの warning logic が main 側に実装済**: `WarnIfOverrideNoEffect` (task-123) / `WarnIfDuplicateMarkerFqn` (task-127) / `WarnIfParameterUnused` (task-128) / `WarnIfNoMarkerFound` (task-120-B Phase 7、 `warnOnEmptyCapture` opt-in flag つき)。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照** すること。

# Warning 基盤 (task-121 で main 側に整備済)

## main module (`compiler-plugin/src/main/kotlin/.../warning/`)

- `CaptureCodeCompilerPluginWarning.kt` — `interface` (全 Warning 実装の親)。 `id` / `message` (English-only) / `reply` の 3 フィールド SSoT
- `WarningContextDsl.kt` — ad-hoc な Warning を組み立てる `warningContext("CC_XXX") { ... }` DSL
- `Warnings.kt` — 複数 feature から throw されうる cross-cutting Warning 用 SSoT (現状空)
- `ReportWarning.kt` — `DiagnosticReporter.reportWarning(warning, source, context, compat, [arg])` 拡張

## feature 配下 (task-123 以降で実装済)

- `feature/<feature>/.../<Logic>Warnings.kt` — その logic から発火する Warning 集約。 実装済の代表:
    - `feature/markerDefinition/fir/validateMarkerAnnotation/MarkerAnnotationWarnings.kt` (`WarnIfOverrideNoEffect` 用)
    - `feature/markerDefinition/MarkerDefinitionWarnings.kt` (cross-logic)
    - `feature/capturedSources/ir/rewriteCapturedSourcesCall/CapturedSourcesWarnings.kt` (`WarnIfNoMarkerFound` 用)

# 必須の使い方 (整備後)

- **生 `messageCollector.report(SEVERITY.WARNING, "...", location)` 禁止** → 必ず `CaptureCodeCompilerPluginWarning` 実装経由
- 各 Warning 実装は `error/` と同じく `id` / `message` (English-only) / `reply` を override
- 警告は **ユーザが対処できる情報** (どのファイル / どの宣言 / 何を直せば消えるか) を必ず含む
- 文面はすべて **English-only** (task-122 で i18n 機構撤去済。 復活が必要なら別 task で API design からやり直す)

# 置いてはいけないもの

- **特定 feature / logic にしか出ない Warning の文面を main `warning/` 直下に追加** — `feature/<feature>/.../<Logic>Warnings.kt` に集約
- **Error 実装** — `compiler-plugin/src/main/.../error/` に置く
- **`BilingualMessage` 等の i18n 機構の復活** — task-122 で撤去済

# Good 例

```kotlin
// compiler-plugin/src/main/.../feature/capturedSources/ir/.../IncludeImportsNoOpWarnings.kt
public object IncludeImportsNoOpWarnings {
    public val NOT_FILE_LEVEL: CaptureCodeCompilerPluginWarning = object : CaptureCodeCompilerPluginWarning {
        override val id: String = "CC_CAPTUREDSOURCES_INCLUDE_IMPORTS_NO_OP"
        override val message: String =
            "includeImports = true is set on ''{0}'' but the target is not a file-level capture; option is ignored."
        override val reply: String? =
            "Apply the marker at file scope (@file:CaptureCode) or remove includeImports = true."
    }
}

// 呼び出し側 (compat-kXXX checker)
reporter.reportWarning(IncludeImportsNoOpWarnings.NOT_FILE_LEVEL, source, context, compat, markerFqn)
```

# Bad 例

```kotlin
messageCollector.report(CompilerMessageSeverity.WARNING, "includeImports is ignored", loc)  // ← NG
```

→ `warning/Warnings.kt` または feature ローカルの `<Logic>Warnings.kt` に実装を追加し、 `reporter.reportWarning(...)` 経由で発火する。

```kotlin
// compiler-plugin/src/main/.../warning/SomeBilingualWarning.kt ← NG (task-122 撤去済)
public val SOME_WARNING = BilingualMessage(en = ..., ja = ...)
```

→ task-122 で i18n 機構は撤去済。 warning 文面も `CaptureCodeCompilerPluginWarning.message` に **English-only** で書く。

---

参考:

- `compiler-plugin/README.md`
- `.claude/rules/compiler-plugin-error.md`
