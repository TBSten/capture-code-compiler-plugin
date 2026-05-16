// ----------------------------------------------------------------------------
// この module は **publish しない** (internal compat layer for K2.0.10+ patches)。
// `:compiler-plugin` の shadowJar に bundled で同梱され、 META-INF/services の
// CompilerCompat factory 経由でロードされる。 詳細は `:compiler-plugin/compat/
// build.gradle.kts` の comment 参照。
//
// task-081: 本 module は 2.0.21 native API (`kotlin-compiler-embeddable-k202`)
// で compile される。 task-080 で識別された 2.0.20+ runtime での drift を、
// 個別 reflection shim ではなく「2.0.21 baseline binary を別 compat module
// として ServiceLoader 経由で dispatch する」 方針で吸収する。
//
// 主な drift (compat-k200 の 2.0.0 baseline binary では NoSuchMethodError /
// NoClassDefFoundError が出るもの):
//   - `IrVarargImplKt` / `IrGetEnumValueImplKt` / `IrConstImplKt` の top-level
//     factory 削除
//   - `IrCallImpl(..., valueArgumentsCount = ...)` constructor 廃止 (top-level
//     factory + symbol から自動計算に変更)
//   - `IrConst<T>` / `IrConstKind<T>` / `FirLiteralExpression<T>` の型パラ削除
//   - `DeepCopyTypeRemapper(SymbolRemapper)` ctor → `(ReferencedSymbolRemapper)`
//     (= `deepCopyWithSymbols` extension の internal 実装変更)
//
// 選択ロジック: `CompatContext.Factory.minVersion = "2.0.10"` を宣言する。
// `resolveFactory` は `<= currentVersion` の中で minVersion が最大のものを選ぶ
// ため:
//   - 2.0.0  → compat-k200  (minVersion 2.0.0)
//   - 2.0.10 → compat-k202  (minVersion 2.0.10、 2.0.0 より新しい)
//   - 2.0.20 → compat-k202
//   - 2.0.21 → compat-k202
//   - 2.1.0+ → compat-k210+ (minVersion 2.1.0 が 2.0.10 より新しい)
// ----------------------------------------------------------------------------
plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    // Kotlin 2.0.21 native API でビルドする。 compileOnly のみなので、 published jar には
    // kotlin-compiler-embeddable を引き込まない (shadowJar も同様)。
    compileOnly(libs.kotlin.compiler.embeddable.k202)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))

    // task-118: main module の feature/markerDefinition / feature/capturedSources 配下に置かれた
    // domain SSOT を参照する。 単純な `compileOnly(project(":compiler-plugin"))` は循環依存に
    // なる (main の apiElements が shadowJar を返し、 shadowJar が compat-k* の jar を bundled
    // で要求するため) ので、 shadowJar を bypass する `mainClassesOnly` outgoing configuration
    // (compileKotlin output directory のみを expose) を引く。 compat-k200 の build.gradle.kts
    // のコメントも参照。
    compileOnly(
        project(
            mapOf("path" to ":compiler-plugin", "configuration" to "mainClassesOnly"),
        ),
    )

    // task-122: 本 module の `K202Diagnostics` 内 renderer は main module の English-only
    // SSoT (`MarkerAnnotationErrors` / `CapturedSourcesCallErrors`) を `<clinit>` で参照する。
    // test runtime で diagnostic factory を trigger する scenario では main module の class が
    // runtime classpath に必要。 main の compileKotlin output を file dependency として
    // testRuntimeOnly に投入する (詳細は compat-k200 の build.gradle.kts コメント参照)。
    testRuntimeOnly(
        project(
            mapOf("path" to ":compiler-plugin", "configuration" to "mainRuntimeClassesOnly"),
        ),
    )

    // 専用 unit test (ServiceLoader-based sanity)。 kctfork を使わず pure JVM test に
    // 留めるため、 kctfork transitive と consumer Kotlin の drift 問題を回避する。
    // CompatContextImpl 自体は K2.0.21 native API で書かれているので testRuntime に
    // kotlin-compiler-embeddable-k202 (= 2.0.21 固定) を入れて ServiceLoader 経由で
    // ロード可能にする。
    testImplementation(libs.kotlin.compiler.embeddable.k202)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
