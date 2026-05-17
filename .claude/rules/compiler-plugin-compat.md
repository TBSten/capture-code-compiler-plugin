---
paths:
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/compat/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/**/*.kt"
---

このディレクトリには **Kotlin Compiler API のバージョン差を吸収する SPI と、 各バージョン固有の impl** を配置する。 task-120-B Phase 1-7 (`0.2.0`) で IR phase logic は main module へ完全集約済のため、 `compat-kXXX/` 配下には **FIR Checker + CompatContext SPI 23 method の actual 実装のみ** が残る。 main module を Kotlin 2.0.0 固定で compile することで runtime drift を排除する戦略は維持されており、 各 compat module は SPI の主体ではなく **薄い adapter** として位置づけられる。

| サブパス | 内容 |
| --- | --- |
| `compiler-plugin/compat/.../compat/*.kt` | **共有 SPI 2 ファイルのみ**: `CompatContext.kt` (23 method) と `KotlinToolingVersion.kt` (dev / Beta-RC track resolver)。 domain 知識禁止 |
| `compiler-plugin/compat-k200/...` `compat-k210/...` 等 | バージョン別 impl。 `CompatContextImpl` (nested `K{XXX}Diagnostics` + 11 IR primitive impl) + `K{XXX}IrVisitors` + FIR Checker (Logic A / F / G / B-fir)。 3-4 .kt + 0-5 .java |

**task-118 注記**: 旧来 `:compiler-plugin:compat` に置いていた **feature 配下の domain SSoT** (CallableId / NormalizeOptions / `*Errors.kt` 等) は task-118 で **main module** (`compiler-plugin/src/main/.../feature/`) に引き上げ済。 各 `compat-kXXX/` からは main の `mainClassesOnly` outgoing 経由で compileOnly 参照する (shadowJar bypass)。 task-122 で diagnostic 文面 SSoT も main 側 `feature/.../<Logic>Errors.kt` (English-only) に集約され、 旧 `CaptureCodeDiagnosticMessages` (BilingualMessage) は撤去済。

**task-120-B Phase 1-7 注記**: 旧来 `:compiler-plugin:compat` に置いていた以下のクラス群も main module へ移動済:

- `CaptureCodePluginConfig` → `compiler-plugin/src/main/.../code/CaptureCodePluginConfig.kt` (Phase 1)
- `CaptureCodeCompatHolder` → `compiler-plugin/src/main/.../compat/CaptureCodeCompatHolder.kt` (Phase 1)
- `CaptureCodePluginConfigHolder` → `compiler-plugin/src/main/.../compat/CaptureCodePluginConfigHolder.kt` (Phase 1)
- `CaptureCodeMessageCollectorHolder` → `compiler-plugin/src/main/.../compat/CaptureCodeMessageCollectorHolder.kt` (Phase 1)
- `CapturedSite` → `compiler-plugin/src/main/.../feature/capturedSources/CapturedSite.kt` (task-118)
- `CaptureCodeMarkerRegistry` → `compiler-plugin/src/main/.../feature/markerDefinition/CaptureCodeMarkerRegistry.kt` (task-118)
- `CaptureCodeExpressionSiteRegistry` → `compiler-plugin/src/main/.../feature/capturedSources/CaptureCodeExpressionSiteRegistry.kt` (task-118)

`:compiler-plugin:compat` には **SPI 本体 (`CompatContext.kt` + `KotlinToolingVersion.kt`) のみ** が残る。

さらに Phase 3-6 で IR logic 本体 (CollectDeclarationSite / RewriteCapturedSourcesCall / BuildMarkerInstance / filler / userargs) と Visitor ベース実装が main 側へ移ったため、 各 `compat-kXXX/` は **3-4 .kt + 0-5 .java** という slim 構造になっている (Phase 6 直前は 12-13 .kt + 0-5 .java)。

**適切でないものを置こうとしていた場合は `compiler-plugin/compat/README.md` を参照** すること。

# 置いてよいもの (`compiler-plugin/compat/.../compat/`)

- `CompatContext.kt` — SPI interface (23 method)。 バージョン差のあるメソッドを宣言:
    - 既存 12 method: `firAdditionalCheckersExtensions(...)` / `registerExtensions(...)` / `literalValueOrNull(...)` / `isLiteralExpression(...)` / `toRegularClassSymbolOrNull(...)` / `classIdOf(...)` / `containingFilePathOf(...)` / `fullyExpandedTypeOf(...)` / `diagnosticFactory(...)` 他
    - task-120-B Phase 2 で追加した 11 IR primitive method: `acceptIrVisitor(...)` / `walkIrFileDeclarations(...)` / `loadFileText(...)` / `putValueArgument(...)` / `createIrCall(...)` / `setTypeArgument(...)` / `valueParametersOf(...)` / `irExpressionBodyOf(...)` / `irConstString(...)` / `irGetEnumValueOf(...)` / `irGetClassReferenceOf(...)`
- `KotlinToolingVersion.kt` — Kotlin バージョンの parser / comparator。 dev track / Beta-RC track を区別する resolver も含む

**`compat/.../compat/` 配下にはこの 2 ファイル以外を置かない**。 holder / config data class / domain registry の追加は main module の `compiler-plugin/src/main/.../compat/` または `feature/` 配下で行う。

# 置いてよいもの (`compiler-plugin/compat-k{200,202,210,220,230,240rc}/...`)

各 module は Phase 6 後の slim 構造で、 以下のみを抱える:

- `CompatContextImpl.kt` — `CompatContext` 実装 + 内側に `Factory : CompatContext.Factory` + nested `K{XXX}Diagnostics` (`KtDiagnosticFactory*` SSoT, task-121) + **11 IR primitive method の actual 実装** (Phase 2)。 AutoService 経由で `META-INF/services/...CompatContext$Factory` に登録
- `K{XXX}IrVisitors.kt` — `acceptIrVisitor` / `walkIrFileDeclarations` の actual 実装。 Visitor base class drift (`IrElementVisitorVoid` ↔ `IrVisitorVoid`) を吸収。 Phase 2 で導入
- `checker/K{XXX}CheckerExtensions.kt` — FIR Checker 群を束ねる `FirAdditionalCheckersExtension`
- `checker/K{XXX}*Checker.kt` — FIR Checker 個々 (`K{XXX}CaptureCodeMarkerClassChecker`, `K{XXX}CapturedSourcesCallChecker`, `K{XXX}MarkerAnnotationChecker`, `K{XXX}ExpressionSiteCollector`)。 task-121 で旧 `K{XXX}CaptureCodeDiagnostics.kt` は `CompatContextImpl.kt` の nested object に集約
- `checker/FullyExpandedTypeShim.kt` (K200/K202 専用) — drift D11 吸収用 reflection shim

各 `compat-kXXX/` は main module の `feature/.../<Logic>Errors.kt` (English-only message SSoT, task-122) を `mainClassesOnly` outgoing 経由で compileOnly 参照する (shadowJar bypass)。 diagnostic factory の renderer は `MarkerAnnotationErrors.IS_EXPECT.message` 形式で直接 main の `.message` を読む。

# 命名規約

- `compat-k<major><minor><patch>/` ディレクトリ (例: `compat-k200`, `compat-k210`, `compat-k240rc`)。 patch 違いは Factory の `minVersion` で吸収するので新 module を作らない (`compat/README.md` 参照)
- パッケージは `me.tbsten.capture.code.compat.k<version>` (例: `me.tbsten.capture.code.compat.k200`)
- AutoService + KSP で `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` を自動生成 (resources/ には書かない)
- `compat/.../compat/` 配下は **plugin の domain 知識禁止**。 SPI 2 ファイル以外を置かない

# 置いてはいけないもの

- **`compat/.../compat/` 配下に SPI 以外のもの** — holder / config / registry は main module 側
- **`compat/.../compat/` 配下に plugin domain 知識** — `@CaptureCode` FQN / dedent algorithm / KDoc 抽出ロジック等は `feature/<feature>/` 配下 (main module) に置く
- **`compat-kXXX/` 配下に IR walker / rewriter / filler / userargs** — Phase 3-5 で main 側に集約済。 新しい IR logic も main module の `feature/capturedSources/ir/` 配下に置く
- **`compat-kXXX/` 配下に複数バージョンで同一のはずのロジック** — 共有して問題ない処理 (例: 純粋な文字列正規化) は main module の `feature/capturedSources/ir/normalize/` 配下に切り出し、 各 `compat-kXXX/` から参照
- **`SourceTextExtractor` 相当の version-sensitive helper を新規追加** — Phase 2 の `loadFileText` SPI method で吸収済。 新規 file text retrieval logic はそこに集約

# Good 例

```kotlin
// compiler-plugin/compat/.../compat/CompatContext.kt
public interface CompatContext {
    // FIR phase
    public fun firAdditionalCheckersExtensions(): List<(FirSession) -> FirAdditionalCheckersExtension>
    public fun literalValueOrNull(expression: FirLiteralExpression): Any?
    // ...

    // IR primitive (task-120-B Phase 2)
    public fun acceptIrVisitor(moduleFragment: IrModuleFragment, visitor: Any)
    public fun walkIrFileDeclarations(
        file: IrFile,
        onClass: (IrClass) -> Unit,
        onSimpleFunction: (IrSimpleFunction) -> Unit,
        onProperty: (IrProperty) -> Unit,
        onTypeAlias: (IrTypeAlias) -> Unit,
    )
    public fun loadFileText(file: IrFile): String?
    public fun createIrCall(symbol: IrFunctionSymbol, type: IrType, ...): IrCall
    // ...

    public interface Factory {
        public val minVersion: KotlinToolingVersion
        public fun create(): CompatContext
    }
}

// compiler-plugin/compat-k200/.../k200/CompatContextImpl.kt
@AutoService(CompatContext.Factory::class)
public class CompatContextImpl : CompatContext {
    override fun firAdditionalCheckersExtensions() = listOf(::K200CheckerExtensions)
    override fun acceptIrVisitor(moduleFragment: IrModuleFragment, visitor: Any) =
        moduleFragment.acceptChildrenVoid(visitor as IrElementVisitorVoid)
    override fun createIrCall(symbol: IrFunctionSymbol, type: IrType, ...) =
        IrCallImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount)
    // ... (11 IR primitive method の actual impl + 12 既存 method)
    public class Factory : CompatContext.Factory { /* minVersion = 2.0.0 */ }
}
```

# Bad 例

```kotlin
// compiler-plugin/compat/.../compat/CaptureCodeMarkerFqn.kt ← NG
internal val CAPTURE_CODE_MARKER_FQN = FqName("me.tbsten.capture.code.CaptureCode")
```

→ `me.tbsten.capture.code` という domain 固有 FQN は `compat/` パッケージに置かない。 `compiler-plugin/src/main/.../feature/markerDefinition/CaptureCodeMetaAnnotation.kt` に置く (main module、 domain OK ゾーン)。

```kotlin
// compiler-plugin/compat-k200/.../K200CapturedSourcesRewriter.kt ← NG (Phase 6 以降)
internal class K200CapturedSourcesRewriter { ... }
```

→ IR rewriter / collector / filler / userargs は task-120-B Phase 3-5 で main 側に集約済。 新規 IR logic は `compiler-plugin/src/main/.../feature/capturedSources/ir/rewriteCapturedSourcesCall/` 配下に置く。 旧 `K{XXX}CapturedSourcesRewriter` 等は Phase 6 で削除済。

```kotlin
// compiler-plugin/compat/.../compat/CapturedSite.kt ← NG (task-118 以降)
public data class CapturedSite(...)
```

→ `CapturedSite` は task-118 で main 側 `compiler-plugin/src/main/.../feature/capturedSources/CapturedSite.kt` に移動済。 compat layer に置かない。

---

参考:

- `compiler-plugin/compat/README.md` — SPI 仕様 + 新バージョン追加スクリプト (`generate-compat-module.sh`)
- `compiler-plugin/README.md` — module 全体構成 + main 側 IR chain 図
- `docs/architecture.md` — task-120-B Phase 1-7 の経緯と影響
