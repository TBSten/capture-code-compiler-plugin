---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/kotlin/me/tbsten/capture/code/` 直下) には、 **Kotlin Compiler Plugin API を継承する最上位エントリポイントだけ** を flat に配置できる。 plugin 業務ロジックや FIR / IR の処理本体は **`compiler-plugin/compat/`** (共有 SPI + 実装) と **`compiler-plugin/compat-kXXX/`** (バージョン別 impl) 配下に置く。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# 置いてよいファイル

現状 root 直下に置かれている 4 ファイルが全てで、 これ以上ファイルを増やす場合は本ルールへの追記を含めて慎重に検討する。

- `CaptureCodeCompilerPluginRegistrar.kt` — `CompilerPluginRegistrar` 継承。 FIR / IR 拡張を `CompilerConfiguration` に登録する束ね役。 `supportsK2 = true`
- `CaptureCodeCommandLineProcessor.kt` — `CommandLineProcessor` 継承。 5 つの CLI option (`includeKdoc` / `includeImports` / `includeAnnotationLines` / `dedent` / `includeLineInfo`) を `CaptureCodePluginConfig` に集約する。 `CAPTURE_CODE_PLUGIN_ID` 定数もここで宣言
- `CaptureCodeFirExtensionRegistrar.kt` — `FirExtensionRegistrar` 継承。 `CaptureCodeCompatHolder.context.firAdditionalCheckersExtensions()` から得た factory リストを `+::` で登録するのみ
- `CaptureCodeIrExtension.kt` — `IrGenerationExtension` 継承。 `CaptureCodeCompatHolder.context.transformIr(...)` に委譲し、 try/finally で `CaptureCodeMarkerRegistry.reset()` / `CaptureCodeExpressionSiteRegistry.reset()` を呼ぶ

`CaptureCodePluginConfig` 本体は **`compiler-plugin/compat/.../CaptureCodePluginConfig.kt`** に置かれており、 root 直下では import するだけ。

# 役割

- **plugin の組み立て役**。 compat layer のエントリ (`CaptureCodeCompatHolder.context`) と `CompilerConfiguration` を繋ぐだけ
- 業務ロジックは持たない。 「どの logic を、 どの順で、 どの条件で動かすか」さえ書かない (compat-kXXX 側の `firAdditionalCheckersExtensions()` / `transformIr()` が決める)
- Kotlin バージョン差は **直接見ない**。 `KotlinVersion` 参照や reflection は禁止 — 全て compat layer 経由

# 置いてはいけないもの

- **FIR Checker / FIR Generator の実装** — `compat-kXXX/.../checker/` 配下
- **IR Transform 実装** — `compat-kXXX/.../K{XXX}IrTransform.kt` 等
- **`@CaptureCode` / `@Marker` 等の domain 知識を持つ helper** — `compat/.../feature/<feature>/` 配下
- **compiler API のラッパー** (`classIdOf` 等) — 将来 `compat/.../utils/` に追加予定
- **Error / Diagnostic 実装** — `compat/.../error/` 配下

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
                // capturedSources<T>() を実 IR に置換 ← compat-kXXX の責務
                moduleFragment.accept(...)
            }
        })
    }
}
```

→ IR 変換は `compiler-plugin/compat-k{200,210}/.../K{XXX}IrTransform.kt` に置く。 `CaptureCodeIrExtension` は `CaptureCodeCompatHolder.context.transformIr(...)` に委譲するだけ。

```kotlin
// FQN / regex 等を root 直下に追加する ← NG
internal val CAPTURE_CODE_MARKER_FQN = FqName("me.tbsten.capture.code.CaptureCode")
```

→ FQN / ClassId / CallableId は **`compat/.../feature/capturedsources/CaptureCodeCallableIds.kt`** や **`compat/.../fir/marker/CaptureCodeMetaAnnotation.kt`** に置く。

---

参考:

- `compiler-plugin/README.md`
- `compiler-plugin/compat/README.md`
