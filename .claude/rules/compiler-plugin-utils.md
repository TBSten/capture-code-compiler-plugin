---
paths:
    - "compiler-plugin/src/main/kotlin/me/tbsten/capture/code/utils/**/*.kt"
---

このディレクトリ (`compiler-plugin/src/main/.../utils/`) は **plugin の domain 知識を持たない、 Kotlin compiler API の純粋な汎用 helper** を置く場所として予約されている。 「他の Kotlin Compiler Plugin プロジェクトにそのままコピペできるか？」が判定基準。

**現状このディレクトリ自体まだ存在しない**。 共通 helper が必要になったら `compiler-plugin/src/main/kotlin/me/tbsten/capture/code/utils/` を作成して flat に置き、 サブディレクトリは phase 別 (`utils/fir/`, `utils/ir/`) で切る。 task-118 以降の方針で **main module 側に置く** (旧 compat 側ではなく)。

**適切でないものを置こうとしていた場合は `compiler-plugin/README.md` を参照して配置場所を再検討** すること。

# 置いてよいもの

- compiler API のラッパー (`ClassId` / `CallableId` / `FqName` の短縮 constructor)
- 共通定型処理 (`IrDeclaration → CompilerMessageLocation` の変換、 `TargetPlatform` 判定)
- 純粋な変換関数 (FIR / IR の symbol → 何か)
- 任意の `ClassId` から annotation を組み立てる generic builder

サブディレクトリは phase 別: `utils/fir/`, `utils/ir/`。

# 置いてはいけないもの

plugin 固有の domain 知識を持つコード全般:

- **特定 annotation の FQN を知っている** (例: `me.tbsten.capture.code.CaptureCode`, `@Marker`)
- **plugin 固有の命名規則を知っている** (例: `capturedSources_<scope>`, marker class id)
- **plugin 固有の hash / canonical key 計算**
- **plugin 固有の annotation を注入する処理**
- **plugin の generated declaration の組み立て / 復元**
- **特定 plugin option の読み取り** (`includeKdoc` / `dedent` 等の解釈)
- **PSI / source text の切り出し処理** — `CompatContext.loadFileText(file)` SPI 経由で各 compat-kXXX の actual 実装に委譲 (task-120-B Phase 2 で domain-agnostic SPI 化、 version-sensitive のため)

これらは **`compiler-plugin/src/main/.../feature/<feature>/` 配下** か **`compat-kXXX/` 配下** に置く。

# Good 例

```kotlin
// utils/CompilerIds.kt — capture-code 固有知識ゼロ
internal fun classIdOf(packageName: String, name: String): ClassId =
    ClassId(FqName(packageName), Name.identifier(name))

// utils/ir/CompilerMessageLocation.kt — capture-code 固有知識ゼロ
internal fun IrDeclaration.compilerMessageLocation(): CompilerMessageLocation? = /* ... */

// utils/fir/AnnotationBuilders.kt — 任意の ClassId に対応した汎用 builder
internal fun ClassId.buildSimpleAnnotation(session: FirSession): FirAnnotation = /* ... */
```

# Bad 例

```kotlin
// utils/fir/CaptureCodeAnnotationPredicates.kt  ← NG: @CaptureCode の FQN を知っている
internal val CaptureCodePredicate = LookupPredicate.create {
    annotated(FqName("me.tbsten.capture.code.CaptureCode"))
}
```

→ `compiler-plugin/src/main/.../feature/markerDefinition/CaptureCodeMetaAnnotation.kt` 配下に置く (task-118 で main 側に集約済)。

```kotlin
// utils/ir/CapturedSourcesCallableId.kt  ← NG: capturedSources() の CallableId
internal val CapturedSourcesCallableId = CallableId(
    FqName("me.tbsten.capture.code"),
    Name.identifier("capturedSources"),
)
```

→ `compiler-plugin/src/main/.../feature/capturedSources/CaptureCodeCallableIds.kt` に既にある (task-118 で main 側に集約済)。 重複定義しない。

# 名前付けの例外

関数名・class 名に `CaptureCode` や `Captured` を含めないのが望ましいが、 **名前で動機を示すだけで logic が汎用なら許容**:

```kotlin
// utils/ir/PlatformUtil.kt
internal val TargetPlatform?.requiresKlibIcSafetyForCapturedSources: Boolean
    get() = !isJvm() && (isJs() || isWasm() || isNative())
```

# チェックリスト

- [ ] `utils/` 配下の新規ファイルは plugin 固有 annotation / 命名規則 / hash / option のいずれも「知らない」
- [ ] 「`utils/` に置きたいが固有知識を含む」場合は callback 切り出し + wrapper を logic 配下に置く設計を検討した
- [ ] サブディレクトリは phase 別 (`fir/` / `ir/`) になっている
- [ ] version-sensitive な処理 (PSI / source text 切り出し等) は `compat-kXXX/` 配下に置いている

---

参考:

- `compiler-plugin/README.md`
