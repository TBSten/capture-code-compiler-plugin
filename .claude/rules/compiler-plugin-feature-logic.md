---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/feature/*/*/**/*.kt"
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/fir/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/me/tbsten/capture/code/compat/k*/checker/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/me/tbsten/capture/code/compat/k*/filler/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/me/tbsten/capture/code/compat/k*/userargs/**/*.kt"
---

このディレクトリ群 (`compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/`, `compat/.../fir/<phase>/`, `compat-k*/.../{checker,filler,userargs}/`) には **その logic に閉じた処理本体と、 その logic 固有の定数・Diagnostic 文面 SSoT** を配置する。

本プロジェクトでは runtime drift 排除のため「logic 本体」は **バージョン依存 (`compat-kXXX/`) と バージョン非依存 (main module `compiler-plugin/src/main/`)** に分かれている (task-118 で feature/ 配下の domain SSoT は main に移管済):

- **`compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/`** — feature/logic に紐づく domain SSoT (例: `validateMarkerAnnotation/MarkerAnnotationErrors.kt`, `normalize/Dedent.kt` のような pure string utility)。 各 `compat-kXXX/` からは `mainClassesOnly` outgoing 経由で compileOnly 参照
- **`compat/.../fir/marker/`** — `@CaptureCode` メタ annotation の compat 共有 domain (FQN / option extractor)。 FIR API drift から独立した部分のみ (task-118 で移動できなかった残骸)
- **`compat-kXXX/.../checker/`** — FIR Checker (`FirDeclarationChecker` / `FirExpressionChecker` 等の継承クラス)。 D9 (`check()` 引数順 drift) のため必ずバージョン依存層
- **`compat-kXXX/.../filler/`** — IR phase で `Captured*` 型を組み立てる builder 群 (IR API drift D5–D8 のためバージョン依存)
- **`compat-kXXX/.../userargs/`** — `@Marker(arg=...)` 引数を IR で再構築する builder

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# logic 命名規約

- ディレクトリ名は **lowerCamelCase or snake_case** で機能内容が予想できるもの (例: `normalize`, `marker`, `checker`, `filler`, `userargs`, `validateMarkerAnnotation`)。 main 側は lowerCamelCase に統一 (task-118)
- 配下の代表クラスはなるべく **`動詞Object`** の命名 (例: `Dedent`, `KdocStrip`, `K200CapturedSourcesRewriter`, `UserArgIrBuilder`)。 ただし Kotlin compiler API 継承クラス (`*Checker`, `*Extension`) は API 慣例に従う
- バージョン依存層のクラスには **`K<XXX>` prefix** を必ず付ける (例: `K200ExpressionSiteCollector`, `K210CapturedSourcesRewriter`)。 これにより同名 class が compat-k200 と compat-k210 で別 bytecode に存在しても classpath 上で衝突しない
- diagnostic 文面 SSoT は **`<LogicName>Errors.kt`** (例: `MarkerAnnotationErrors.kt`, `CapturedSourcesCallErrors.kt`) で main 側 logic 直下に配置

# 置いてよいもの

- 動詞句に対応する処理本体クラス (`Dedent.kt`, `K{XXX}CapturedSourcesCollector.kt`, `K{XXX}CapturedSourcesRewriter.kt` 等)
- Kotlin compiler API 継承クラス (`K{XXX}CaptureCodeMarkerClassChecker extends FirRegularClassChecker`, `K{XXX}CheckerExtensions extends FirAdditionalCheckersExtension` 等)
- **その logic 内だけで使う定数** (1 つの logic 専用 helper など)
- **その logic 固有の Diagnostic 文面 SSoT** — `<LogicName>Errors.kt` (English-only `message` + optional `reply`)。 task-122 以降は feature ローカルに集約
- **その logic の `KtDiagnosticFactory*` 宣言は `compat-kXXX/.../CompatContextImpl.kt` の nested `K{XXX}Diagnostics` に置く** (task-121 で集約)
- orchestrator (sub-logic がある場合の `K{XXX}IrTransform.kt` 等) + 共有 `K{XXX}BuildContext`

# 置いてはいけないもの

- **複数 logic が参照する定数** — 一段上の `feature/<feature>/` 直下に引き上げる
- **複数 feature が参照する Error 基盤** — `compiler-plugin/src/main/.../error/` (interface, DSL, ReportError 等) または `compat/.../error/CaptureCodeFillerClassIds.kt` のような共有 SSoT
- **plugin 全体の Registrar / Extension** — `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/` root 直下に置く
- **compiler API ラッパー** (domain 非依存) — 将来 `compat/.../utils/` に置く

# 引き上げ判断 (logic → feature 直下 / compat-kXXX → main 共有)

以下のいずれかに該当したら **一段上のレイヤに引き上げる**:

- FIR Checker と IR const-fold で同じ regex / FQN を参照したい → `feature/<feature>/` 直下 (main module)
- compat-k200 と compat-k210 で完全に同じ純粋関数 (string 操作 / data class 変換) を実装している → `feature/<feature>/<phase>/<logic>/` 配下 (main module、 例: `capturedSources/ir/normalize/`)
- diagnostic 文面が複数 compat-kXXX で同じ → `feature/<feature>/.../<Logic>Errors.kt` (main module、 English-only SSoT)

# Good 例

```kotlin
// compiler-plugin/compat-k200/.../checker/K200CapturedSourcesCallChecker.kt
internal class K200CapturedSourcesCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    override fun check(expression: FirFunctionCall, context: CheckerContext, reporter: DiagnosticReporter) {
        val callableId = expression.calleeReference.toResolvedCallableSymbol()?.callableId ?: return
        if (callableId != CaptureCodeCallableIds.capturedSources) return
        // 型引数 T の検査 (Logic G)
        // ...
    }
}
```

`CaptureCodeCallableIds` は main の feature 直下から参照、 checker class は K200 固有 module に閉じる。

```kotlin
// compiler-plugin/src/main/.../feature/capturedSources/ir/normalize/Dedent.kt
public fun dedent(lines: List<String>): List<String> {
    val minIndent = lines.filter { it.isNotBlank() }.minOfOrNull { it.takeWhile(Char::isWhitespace).length } ?: 0
    return lines.map { if (it.isBlank()) it else it.drop(minIndent) }
}
```

pure function なので compat-k200 / compat-k210 で共有。 main module に置いて `mainClassesOnly` 経由で参照。

```kotlin
// compiler-plugin/src/main/.../feature/markerDefinition/fir/validateMarkerAnnotation/MarkerAnnotationErrors.kt
public object MarkerAnnotationErrors {
    public val IS_EXPECT: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_MARKER_IS_EXPECT"
        override val message: String = "@CaptureCode marker annotation cannot be declared as 'expect'. ..."
        override val reply: String? = "Remove the 'expect' modifier; ..."
    }
}
```

各 `compat-kXXX/.../CompatContextImpl.kt` の `K{XXX}CaptureCodeDefaultMessages` から `.message` 参照。

# Bad 例

```kotlin
// compiler-plugin/compat-k200/.../K200Dedent.kt
internal fun k200Dedent(lines: List<String>): List<String> = /* compat-k210 と同じ実装 */
```

→ pure function は `feature/capturedSources/ir/normalize/Dedent.kt` (main) に置いて共有。 compat-kXXX 配下に重複しない。

```kotlin
// compiler-plugin/compat-k200/.../checker/CapturedSourcesCallChecker.kt  ← NG: K200 prefix なし
internal class CapturedSourcesCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) { ... }
```

→ compat-k200 / compat-k210 で同名 class が衝突する可能性があるので **必ず `K200CapturedSourcesCallChecker` のように K{XXX} prefix を付ける**。

```kotlin
// compiler-plugin/compat-k200/.../checker/K200CaptureCodeMarkerVisibilityMessage.kt  ← NG
internal val MARKER_VISIBILITY_MESSAGE_EN = "Marker annotation must be 'internal' or 'private'."
```

→ 診断文面は main 側の `feature/<feature>/.../<Logic>Errors.kt` に English-only で集約 (task-122)。 各 K{XXX} の `K{XXX}CaptureCodeDefaultMessages` の renderer から `.message` 参照する。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
