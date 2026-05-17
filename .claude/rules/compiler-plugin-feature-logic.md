---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/feature/*/*/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/me/tbsten/capture/code/compat/k*/checker/**/*.kt"
---

このディレクトリ群 (`compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/`, `compat-k*/.../checker/`) には **その logic に閉じた処理本体と、 その logic 固有の定数・Diagnostic 文面 SSoT** を配置する。

本プロジェクトでは runtime drift 排除のため「logic 本体」は **バージョン依存 (`compat-kXXX/.../checker/`) と バージョン非依存 (main module `compiler-plugin/src/main/`)** に分かれている (task-118 で feature/ 配下の domain SSoT は main に、 task-120-B Phase 3-5 で IR logic 本体も main に移管済):

- **`compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/`** — feature/logic に紐づく domain SSoT と **logic 本体** (例: `validateMarkerAnnotation/MarkerAnnotationErrors.kt`, `normalize/Dedent.kt` のような pure string utility, `collectDeclarationSite/CollectDeclarationSite.kt`, `rewriteCapturedSourcesCall/buildMarkerInstance/filler/FillSource.kt` 等)。 各 `compat-kXXX/` からは `mainClassesOnly` outgoing 経由で compileOnly 参照
- **`compat-kXXX/.../checker/`** — FIR Checker (`FirDeclarationChecker` / `FirExpressionChecker` 等の継承クラス) と FIR Checker dispatcher (`K{XXX}CheckerExtensions`)。 drift D9 (`check()` 引数順 drift) と Visitor base class drift のため必ずバージョン依存層

**task-120-B Phase 6 注記**: 旧 `compat-k*/.../filler/` `compat-k*/.../userargs/` ディレクトリは削除済 (元 `K{XXX}CapturedSourcesCollector.kt`, `K{XXX}CapturedSourcesRewriter.kt`, `K{XXX}IrTransform.kt`, `SourceTextExtractor.kt`, `filler/*.kt`, `userargs/*.kt` も同時削除)。 IR logic 本体は main 側 `feature/capturedSources/ir/` 配下に集約され、 IR drift D5-D8 は `CompatContext` SPI の 11 IR primitive method で吸収される (`createIrCall`, `putValueArgument`, `setTypeArgument`, `valueParametersOf`, `irExpressionBodyOf`, `irConstString`, `irGetEnumValueOf`, `irGetClassReferenceOf`, `acceptIrVisitor`, `walkIrFileDeclarations`, `loadFileText`)。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# logic 命名規約

- ディレクトリ名は **lowerCamelCase or snake_case** で機能内容が予想できるもの (例: `normalize`, `marker`, `checker`, `filler`, `userargs`, `validateMarkerAnnotation`)。 main 側は lowerCamelCase に統一 (task-118)
- 配下の代表クラスはなるべく **`動詞Object`** の命名 (例: `Dedent`, `KdocStrip`, `K200CapturedSourcesRewriter`, `UserArgIrBuilder`)。 ただし Kotlin compiler API 継承クラス (`*Checker`, `*Extension`) は API 慣例に従う
- バージョン依存層のクラスには **`K<XXX>` prefix** を必ず付ける (例: `K200ExpressionSiteCollector`, `K210CapturedSourcesRewriter`)。 これにより同名 class が compat-k200 と compat-k210 で別 bytecode に存在しても classpath 上で衝突しない
- diagnostic 文面 SSoT は **`<LogicName>Errors.kt`** (例: `MarkerAnnotationErrors.kt`, `CapturedSourcesCallErrors.kt`) で main 側 logic 直下に配置

# 置いてよいもの

main 側 (`compiler-plugin/src/main/.../feature/<feature>/<phase>/<logic>/`):
- 動詞句に対応する処理本体クラス (`Dedent.kt`, `CollectDeclarationSite.kt`, `RewriteCapturedSourcesCall.kt`, `BuildMarkerInstance.kt`, `FillSource.kt`, `BuildUserArg.kt` 等)。 task-120-B Phase 3-4 で IR logic 本体もここに集約
- **その logic 内だけで使う定数** (1 つの logic 専用 helper など)
- **その logic 固有の Diagnostic 文面 SSoT** — `<LogicName>Errors.kt` / `<LogicName>Warnings.kt` (English-only `message` + optional `reply`)。 task-122 以降は feature ローカルに集約

compat-kXXX 側 (`compiler-plugin/compat-k*/.../checker/`):
- Kotlin compiler API 継承クラス (`K{XXX}CaptureCodeMarkerClassChecker extends FirRegularClassChecker`, `K{XXX}CheckerExtensions extends FirAdditionalCheckersExtension`, `K{XXX}MarkerAnnotationChecker`, `K{XXX}CapturedSourcesCallChecker`, `K{XXX}ExpressionSiteCollector` 等)
- FIR Checker drift D9 を吸収する Java Shim (`K{XXX}*Shim.java`, 2.2.x+)
- `FullyExpandedTypeShim` (K200/K202 専用) など reflection-based drift shim
- **その logic の `KtDiagnosticFactory*` 宣言は `compat-kXXX/.../CompatContextImpl.kt` の nested `K{XXX}Diagnostics` に置く** (task-121 で集約)

# 置いてはいけないもの

- **IR logic 本体を `compat-kXXX/` 配下に置く** — task-120-B Phase 6 で IR logic は main 側に集約済 (`feature/capturedSources/ir/`)。 新規 IR logic も main module に置く
- **複数 logic が参照する定数** — 一段上の `feature/<feature>/` 直下に引き上げる
- **複数 feature が参照する Error 基盤** — `compiler-plugin/src/main/.../error/` (interface, DSL, ReportError 等)
- **plugin 全体の Registrar / Extension** — `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/` root 直下に置く
- **compiler API ラッパー** (domain 非依存) — 将来 `compiler-plugin/src/main/.../utils/` に置く

# 引き上げ判断 (logic → feature 直下 / compat-kXXX → main 共有)

以下のいずれかに該当したら **一段上のレイヤに引き上げる**:

- FIR Checker と IR const-fold で同じ regex / FQN を参照したい → `feature/<feature>/` 直下 (main module)
- compat-k200 と compat-k210 で完全に同じ純粋関数 (string 操作 / data class 変換) を実装している → `feature/<feature>/<phase>/<logic>/` 配下 (main module、 例: `capturedSources/ir/normalize/`)
- diagnostic 文面が複数 compat-kXXX で同じ → `feature/<feature>/.../<Logic>Errors.kt` (main module、 English-only SSoT)
- compat-k200 と compat-k210 で実質同じ IR builder logic を持っている → main 側 `feature/<feature>/ir/` に集約し、 IR drift だけ `CompatContext` SPI primitive method (`createIrCall`, `putValueArgument`, `setTypeArgument` 等) に押し込む (task-120-B Phase 2-5 でこの方針が確立)

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
// compiler-plugin/compat-k200/.../K200CapturedSourcesRewriter.kt  ← NG (Phase 6 以降)
internal class K200CapturedSourcesRewriter { /* IR 置換処理 */ }
```

→ task-120-B Phase 6 で旧 `K{XXX}CapturedSourcesRewriter` / `K{XXX}CapturedSourcesCollector` / `K{XXX}IrTransform` / `filler/*` / `userargs/*` は削除済。 IR logic 本体は main 側 `feature/capturedSources/ir/rewriteCapturedSourcesCall/RewriteCapturedSourcesCall.kt` (et al.) に集約。 各 compat-kXXX には IR primitive SPI method の actual 実装 (`CompatContextImpl.kt` 内) と FIR Checker (`checker/` 配下) のみが残る。

```kotlin
// compiler-plugin/compat-k200/.../checker/CapturedSourcesCallChecker.kt  ← NG: K200 prefix なし
internal class CapturedSourcesCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) { ... }
```

→ compat-k200 / compat-k210 で同名 class が衝突する可能性があるので **必ず `K200CapturedSourcesCallChecker` のように K{XXX} prefix を付ける**。

```kotlin
// compiler-plugin/compat-k200/.../checker/K200CaptureCodeMarkerVisibilityMessage.kt  ← NG
internal val MARKER_VISIBILITY_MESSAGE_EN = "Marker annotation must be 'internal' or 'private'."
```

→ 診断文面は main 側の `feature/<feature>/.../<Logic>Errors.kt` に English-only で集約 (task-122)。 各 K{XXX} の `K{XXX}Diagnostics` の renderer から `.message` 参照する。

---

参考:

- `compiler-plugin/README.md`
- `docs/architecture.md` — プロジェクト全体アーキテクチャ
