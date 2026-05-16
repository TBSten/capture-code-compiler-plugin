---
paths:
    - "compiler-plugin/compat/src/main/kotlin/me/tbsten/capture/code/compat/**/*.kt"
    - "compiler-plugin/compat-k*/src/main/kotlin/**/*.kt"
---

このディレクトリには **Kotlin Compiler API のバージョン差を吸収する SPI と、 各バージョン固有の impl** を配置する。 ただし本プロジェクトでは runtime drift (FIR checker `check()` 引数順 drift 等) を完全に排除するため、 **`compat-kXXX/` 配下に FIR Checker / IR Transform 等の業務ロジックも置いている** 点に注意。 これは「main module を Kotlin 2.0.0 固定で compile することで runtime drift を排除する」ための例外措置で、 一般的な compat layer のスコープを超えている。

| サブパス | 内容 |
| --- | --- |
| `compiler-plugin/compat/.../compat/*.kt` | 共有 SPI 本体。 `CompatContext` / `CaptureCodeCompatHolder` / `CapturedSite` / `CaptureCodeMarkerRegistry` / `CaptureCodeExpressionSiteRegistry` / `KotlinToolingVersion` 等。 domain 知識禁止 |
| `compiler-plugin/compat-k200/...`, `compat-k210/...` | バージョン別 impl。 `CompatContextImpl` (nested `K{XXX}Diagnostics` 含む) + `Factory` + 業務ロジック (checker / filler / userargs / IrTransform / SourceTextExtractor) を含む |

**task-118 注記**: 旧来 `:compiler-plugin:compat` に置いていた **feature 配下の domain SSoT** (CallableId / NormalizeOptions / `*Errors.kt` 等) は task-118 で **main module** (`compiler-plugin/src/main/.../feature/`) に引き上げ済。 各 `compat-kXXX/` からは main の `mainClassesOnly` outgoing 経由で compileOnly 参照する (shadowJar bypass)。 task-122 で diagnostic 文面 SSoT も main 側 `feature/.../<Logic>Errors.kt` (English-only) に集約され、 旧 `CaptureCodeDiagnosticMessages` (BilingualMessage) は撤去済。

**適切でないものを置こうとしていた場合は `compiler-plugin/compat/README.md` を参照** すること。

# 置いてよいもの (`compiler-plugin/compat/.../compat/`)

- `CompatContext.kt` — SPI interface。 バージョン差のあるメソッドを宣言 (`transformIr(...)`, `firAdditionalCheckersExtensions()`, `literalValueOrNull(...)`, `toRegularClassSymbolOrNull(...)`, `classIdOf(...)` 等)
- `CaptureCodeCompatHolder.kt` — process-scoped lazy。 `ServiceLoader` を 1 回だけ走らせて `CompatContext` を解決し、 全 compile session で共有
- `KotlinToolingVersion.kt` — Kotlin バージョンの parser / comparator。 dev track / Beta-RC track を区別する resolver も含む
- `CapturedSite.kt` — IR phase で marker site 情報を運ぶ data class
- `CaptureCodeMarkerRegistry.kt` / `CaptureCodeExpressionSiteRegistry.kt` — FIR → IR の compilation-scoped holder。 `IrExtension.generate()` 終了時に `reset()` を呼ぶ前提
- `CaptureCodeMarkerOptions.kt` — `@CaptureCode` の per-marker option を集約する data class

# 置いてよいもの (`compiler-plugin/compat-k{200,210}/...`)

各 module はそのバージョン専用の **完全な業務ロジック** を抱える (runtime drift 排除方針)。 全 module で対称な構造:

- `CompatContextImpl.kt` — `CompatContext` 実装 + 内側に `Factory : CompatContext.Factory` + nested `K{XXX}Diagnostics` (`KtDiagnosticFactory*` SSoT, task-121)。 AutoService 経由で `META-INF/services/...CompatContext$Factory` に登録
- `K{XXX}IrTransform.kt` — IR phase 全体の orchestrator
- `K{XXX}CapturedSourcesCollector.kt` / `K{XXX}CapturedSourcesRewriter.kt` — Logic B / D の IR-side 処理
- `SourceTextExtractor.kt` — KtFile / PsiElement から source text を切り出す helper (FIR drift D5–D8 の吸収点)
- `checker/` — FIR Checker (`K{XXX}CaptureCodeMarkerClassChecker`, `K{XXX}CapturedSourcesCallChecker`, `K{XXX}MarkerAnnotationChecker`, `K{XXX}ExpressionSiteCollector`, `K{XXX}CheckerExtensions`)。 task-121 で旧 `K{XXX}CaptureCodeDiagnostics.kt` は `CompatContextImpl.kt` の nested object に集約
- `filler/` — IR の filler builder (`FillerBuilder`, `CaptureKindFillerBuilder`, `SourceFillerBuilder`, `SourceLocationFillerBuilder`)
- `userargs/` — `@Marker(...)` 引数の IR 表現組み立て (`UserArgIrBuilder`, `UserArgPrimitiveIrBuilder`)

各 `compat-kXXX/` は main module の `feature/.../<Logic>Errors.kt` (English-only message SSoT, task-122) を `mainClassesOnly` outgoing 経由で compileOnly 参照する (shadowJar bypass)。 diagnostic factory の renderer は `MarkerAnnotationErrors.IS_EXPECT.message` 形式で直接 main の `.message` を読む。

# 命名規約

- `compat-k<major><minor><patch>/` ディレクトリ (例: `compat-k200`, `compat-k210`)。 patch 違いは Factory の `minVersion` で吸収するので新 module を作らない (`compat/README.md` 参照)
- パッケージは `me.tbsten.capture.code.compat.k<version>` (例: `me.tbsten.capture.code.compat.k200`)
- AutoService + KSP で `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` を自動生成 (resources/ には書かない)
- `compat/.../compat/` 配下は **plugin の domain 知識禁止**。 ただし `feature/` / `fir/` / `error/` 等は `compat/src/main/kotlin/me/tbsten/capture/code/` 直下 (= `compat/` パッケージ**外**) に置くので、 そちらは別ルールが適用される

# 置いてはいけないもの

- **`compat/.../compat/` 配下に plugin domain 知識** — `@CaptureCode` FQN / dedent algorithm / KDoc 抽出ロジック等は `feature/<feature>/` 配下に置く
- **`compat-kXXX/` 配下に複数バージョンで同一のはずのロジック** — 共有して問題ない処理 (例: 純粋な文字列正規化) は `compiler-plugin/compat/.../feature/captured_sources/normalize/` 配下に切り出し、 各 `compat-kXXX/` から参照
- **`SourceTextExtractor` のような version-sensitive な helper を `compat/` (共有) に置く** — PSI / Fir AST API が drift しているので必ず `compat-kXXX/` 配下

# Good 例

```kotlin
// compiler-plugin/compat/.../compat/CompatContext.kt
public interface CompatContext {
    public fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    )
    public fun firAdditionalCheckersExtensions(): List<(FirSession) -> FirAdditionalCheckersExtension>

    public interface Factory {
        public val minVersion: KotlinToolingVersion
        public fun create(): CompatContext
    }
}

// compiler-plugin/compat-k200/.../k200/CompatContextImpl.kt
@AutoService(CompatContext.Factory::class)
public class CompatContextImpl : CompatContext {
    override fun transformIr(...) = K200IrTransform()(moduleFragment, pluginContext, config)
    override fun firAdditionalCheckersExtensions() = listOf(::K200CheckerExtensions)
    public class Factory : CompatContext.Factory { /* minVersion = 2.0.0 */ }
}
```

# Bad 例

```kotlin
// compiler-plugin/compat/.../compat/CaptureCodeMarkerFqn.kt ← NG
internal val CAPTURE_CODE_MARKER_FQN = FqName("me.tbsten.capture.code.CaptureCode")
```

→ `me.tbsten.capture.code` という domain 固有 FQN は `compat/` パッケージに置かない。 `compiler-plugin/compat/.../fir/marker/CaptureCodeMetaAnnotation.kt` に置く (こちらは domain OK ゾーン)。

```kotlin
// compiler-plugin/compat-k200/.../K200CaptureCodeFqn.kt ← NG
// バージョン非依存の FQN を compat-kXXX 配下に重複定義
internal val MARKER_FQN_K200 = FqName("me.tbsten.capture.code.CaptureCode")
```

→ 共有可能な定数は `compat/.../fir/marker/` 等にまとめ、 各 `compat-kXXX/` から参照する。

---

参考:

- `compiler-plugin/compat/README.md` — SPI 仕様 + 新バージョン追加スクリプト (`generate-compat-module.sh`)
- `compiler-plugin/README.md`
