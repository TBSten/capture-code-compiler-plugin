---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/kotlin/me/tbsten/capture/code/` 直下) には、 **Kotlin Compiler Plugin API を継承する最上位エントリポイント + `CaptureCodePluginConfig` data class** を flat に配置できる。 plugin 業務ロジックや FIR / IR の処理本体は **`compiler-plugin/src/main/kotlin/.../feature/`** (logic 本体、 task-118 + task-120-B Phase 3-5 で main 集約済) と **`compiler-plugin/compat-kXXX/`** (バージョン別 FIR Checker + IR primitive impl) 配下に置く。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# 置いてよいファイル

現状 root 直下に置かれている 5 ファイルが全てで、 これ以上ファイルを増やす場合は本ルールへの追記を含めて慎重に検討する。

- `CaptureCodeCompilerPluginRegistrar.kt` — `CompilerPluginRegistrar` 継承。 FIR / IR 拡張を `CompilerConfiguration` に登録する束ね役。 `supportsK2 = true`
- `CaptureCodeCommandLineProcessor.kt` — `CommandLineProcessor` 継承。 6 つの CLI option (`includeKdoc` / `includeImports` / `includeAnnotationLines` / `dedent` / `includeLineInfo` / `warnOnEmptyCapture`) を `CaptureCodePluginConfig` に集約する。 `CAPTURE_CODE_PLUGIN_ID` 定数もここで宣言
- `CaptureCodeFirExtensionRegistrar.kt` — `FirExtensionRegistrar` 継承。 `CaptureCodeCompatHolder.context.firAdditionalCheckersExtensions()` から得た factory リストを `+::` で登録するのみ
- `CaptureCodeIrExtension.kt` — `IrGenerationExtension` 継承。 task-120-B Phase 5 以降は main 側 IR chain (`CollectDeclarationSite` → `RewriteCapturedSourcesCall` → `WarnIfNoMarkerFound`) を直接呼び出し、 try/finally で `CaptureCodeMarkerRegistry.reset()` / `CaptureCodeExpressionSiteRegistry.reset()` を呼ぶ
- `CaptureCodePluginConfig.kt` — 6 option を集約する data class (task-120-B Phase 1 で compat → main 移管)

# 関連: `compat/` 直下 (3 ファイル)

`CaptureCodePluginConfig` 以外の holder 系も Phase 1 で main 側 `compiler-plugin/src/main/.../compat/` に移管:

- `CaptureCodeCompatHolder.kt` — process-scoped lazy CompatContext holder (ServiceLoader 解決)
- `CaptureCodePluginConfigHolder.kt` — plugin config holder (@Volatile, plugin registration 毎に更新)
- `CaptureCodeMessageCollectorHolder.kt` — MessageCollector holder

# 役割

- **plugin の組み立て役**。 compat layer のエントリ (`CaptureCodeCompatHolder.context`) と `CompilerConfiguration` を繋ぐだけ
- 業務ロジックは持たない。 「どの logic を、 どの順で、 どの条件で動かすか」さえ書かない (compat-kXXX 側の `firAdditionalCheckersExtensions()` / `transformIr()` が決める)
- Kotlin バージョン差は **直接見ない**。 `KotlinVersion` 参照や reflection は禁止 — 全て compat layer 経由

# 置いてはいけないもの

- **FIR Checker / FIR Generator の実装** — `compat-kXXX/.../checker/` 配下
- **IR logic 本体 (CollectDeclarationSite / RewriteCapturedSourcesCall / BuildMarkerInstance / filler / userargs)** — task-120-B Phase 3-5 で main 側 `feature/capturedSources/ir/` 配下に集約済
- **`@CaptureCode` / `@Marker` 等の domain 知識を持つ helper** — `compiler-plugin/src/main/.../feature/<feature>/` 配下
- **compiler API のラッパー** (`classIdOf` 等) — 将来 `compiler-plugin/src/main/.../utils/` に追加予定
- **Error / Diagnostic 実装** — `compiler-plugin/src/main/.../error/` 配下
- **CompatContext SPI の IR primitive method actual 実装** — `compat-kXXX/.../CompatContextImpl.kt` の override に置く

# Good 例

```kotlin
// CaptureCodeFirExtensionRegistrar.kt — 組み立てだけ
public class CaptureCodeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        val compat = CaptureCodeCompatHolder.context
        for (factory in compat.firAdditionalCheckersExtensions()) {
            +factory
        }
    }
}
```

compat layer から factory のリストを受け取って登録するだけで、 checker 自体は知らない。

# Bad 例

```kotlin
// CaptureCodeCompilerPluginRegistrar.kt の中で IR 変換ロジックを直書き ← NG
class CaptureCodeCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                // capturedSources<T>() を実 IR に置換 ← main 側 IR chain の責務
                moduleFragment.accept(...)
            }
        })
    }
}
```

→ IR 変換 logic は task-120-B Phase 3-5 で **main module の `compiler-plugin/src/main/.../feature/capturedSources/ir/rewriteCapturedSourcesCall/`** に集約済。 `CaptureCodeIrExtension` から `RewriteCapturedSourcesCall` 等を直接 invoke し、 IR API drift は `CaptureCodeCompatHolder.context` 経由で 11 IR primitive method に委譲する。

```kotlin
// FQN / regex 等を root 直下に追加する ← NG
internal val CAPTURE_CODE_MARKER_FQN = FqName("me.tbsten.capture.code.CaptureCode")
```

→ FQN / ClassId / CallableId は **`compiler-plugin/src/main/.../feature/capturedSources/CaptureCodeCallableIds.kt`** や **`compiler-plugin/src/main/.../feature/markerDefinition/CaptureCodeMetaAnnotation.kt`** に置く (task-118 で main 側に集約済)。

---

参考:

- `compiler-plugin/README.md`
- `compiler-plugin/compat/README.md`
