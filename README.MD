# CaptureCode

A Kotlin compiler plugin that **captures the source string at compile time** simply by marking code with an annotation you define yourself, then lets you read it back as data at runtime. No reflection, zero runtime cost.

```kotlin
import me.tbsten.capture.code.*

@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippet(
    val source: Source = Source(),
)

@Snippet fun greet() = "Hello!"
@Snippet fun farewell() = "Goodbye!"

fun main() {
    capturedSources<Snippet>().forEach { println(it.source.value) }
    // → fun greet() = "Hello!"
    // → fun farewell() = "Goodbye!"
}
```

Define a single annotation marked with `@CaptureCode`. Every declaration, expression, or file you mark with it becomes a capture target. Calling `capturedSources<T>()` returns a list of annotation instances, each carrying **the source string of that site verbatim**.

---

## You can also carry your own parameters

The filler types (`Source` / `SourceLocation` / `CaptureKind`) coexist with your own parameters (`id`, `label`, `priority`, etc.). Fillers are identified by type, so any value you set at the use site is preserved as-is for your own parameters.

```kotlin
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class HttpRoute(
    val method: Method,                                 // ← your own value
    val path: String,                                   // ← your own value
    val source: Source = Source(),                      // ← filled in by the plugin
    val location: SourceLocation = SourceLocation(),    // ← filled in by the plugin
) {
    enum class Method { GET, POST, PUT, DELETE }
}

@HttpRoute(method = HttpRoute.Method.GET, path = "/users")
fun listUsers() = "[]"

fun main() {
    capturedSources<HttpRoute>().forEach { r ->
        println("${r.method} ${r.path}  @ ${r.location.filePath}:${r.location.startLine}")
        println(r.source.value)
    }
}
```

---

## Per-marker option override

`@CaptureCode` accepts optional parameters that **override the Gradle DSL config on a per-marker basis**. Use this when a particular marker needs different defaults (e.g. one marker should preserve indentation, another should always include KDoc) without changing the project-wide config.

```kotlin
// Force KDoc on for this marker only
@CaptureCode(includeKdoc = CaptureCode.Override.Yes)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DocSample(val source: Source = Source())

// Force dedent off so the raw indentation is preserved
@CaptureCode(dedent = CaptureCode.Override.No)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class RawSnippet(val source: Source = Source())
```

Each `@CaptureCode` parameter is a tri-state `CaptureCode.Override` enum:

| Value | Effective config |
|---|---|
| `Override.Default` (default) | Falls through to the Gradle DSL config |
| `Override.Yes` | Force `true` for this marker |
| `Override.No` | Force `false` for this marker |

The five parameters mirror the Gradle DSL options:

| Marker parameter | Gradle DSL option |
|---|---|
| `includeKdoc` | `captureCode { includeKdoc }` |
| `includeImports` | `captureCode { includeImports }` |
| `includeAnnotationLines` | `captureCode { includeAnnotationLines }` |
| `dedent` | `captureCode { dedent }` |
| `includeLineInfo` | `captureCode { includeLineInfo }` |

Existing markers that pass no arguments (`@CaptureCode`) keep their existing behaviour — every parameter defaults to `Override.Default`, which means "use the Gradle config as before".

---

## Filler types provided by the library

Declare any of these as a parameter type on your marker annotation and the plugin will fill in the value. **If you don't declare it, nothing happens (opt-in).**

| Type | Role |
|---|---|
| `Source(val value: String)` | The captured source string |
| `SourceLocation(packageName, filePath, startLine, endLine)` | Location info for the capture site |
| `CaptureKind(val value: Kind)` | `EXPRESSION` / `PROPERTY` / `CLASS` / `OBJECT` / `FUNCTION` / `TYPEALIAS` / `FILE` |

---

## Where you can capture

```kotlin
// declarations
@Marker val prop = 1
@Marker class MyClass
@Marker fun myFun() = 2
@Marker object MyObj
@Marker typealias MyAlias = Int

// whole file
@file:Marker

// expressions
val r = @Marker (1 + 2 + 3)
val block = @Marker run { ... }
```

---

## Use cases

- **Documentation generation** — stop writing the same code sample twice in prose and in the implementation
- **Catalogs of CLI commands / REST routes**
- **Collecting migration scripts and DB schema declarations**
- **Inventories of feature flags and config keys**
- **Registering benchmarks and test fixtures**
- **DI binding catalogs** (à la Anvil / Metro)
- **Step collection for tutorials and BDD scenarios**

---

## Quick Start

### JVM project

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    id("me.tbsten.capture.code") version "0.1.2"
}

captureCode {
    includeKdoc = true        // default: true
    includeImports = false    // default: false (only affects @file: captures)
    includeAnnotationLines = false  // default: false
    dedent = true             // default: true
    includeLineInfo = true    // default: true
}

repositories { mavenCentral() }
```

The Gradle plugin automatically adds the `me.tbsten.capture.code:annotation`
runtime to your `implementation` configuration in `afterEvaluate`. You only
need to apply the plugin.

### Kotlin Multiplatform project

```kotlin
plugins {
    kotlin("multiplatform") version "2.1.0"
    id("me.tbsten.capture.code") version "0.1.2"
}

kotlin {
    jvm()
    androidTarget()                 // requires com.android.library plugin + android { ... } block
    js(IR) { browser(); nodejs() }
    wasmJs { browser(); nodejs() }
    linuxX64()
    // Apple targets require Xcode; see below.
}

captureCode {
    dedent = true
}
```

For KMP projects, the annotation runtime is added to `commonMainImplementation`
automatically.

> [!IMPORTANT]
> Marker definitions, use sites, and `capturedSources<T>()` calls must all
> live in **the same Kotlin compilation invocation** (the same source-set
> tree). The plugin's in-process registries are scoped to a single
> invocation. For test fixtures, that means placing all three in the test
> source sets (`commonTest` / `jvmTest` / …). See
> [docs/known-limitations.md](docs/known-limitations.md) for details.

### Samples

Runnable samples are available under [`samples/`](samples/):

- [`samples/jvm-sample/`](samples/jvm-sample/) — minimum JVM setup with a
  5-case cookbook covering markers, fillers, user-defined parameters,
  every declaration target, and `@file:` capture.
- [`samples/kmp-sample/`](samples/kmp-sample/) — KMP setup that captures
  inside test source sets across `jvm` / `js` / `linuxX64` / `mingwX64`.

---

## Architecture overview

The plugin is split into a runtime stub, a compiler plugin, a thin Gradle
plugin and a compat layer that absorbs Kotlin compiler API drift.

```
:annotation                     // @CaptureCode, filler types, capturedSources<T>() stub
:compiler-plugin                // FIR + IR extensions
  :compiler-plugin:compat       // CompatContext interface
  :compiler-plugin:compat-k200  // Kotlin 2.0.x impl
  :compiler-plugin:compat-k210  // Kotlin 2.1.x impl
:gradle-plugin                  // KotlinCompilerPluginSupportPlugin
```

The FIR phase discovers `@CaptureCode`-meta-annotated markers, checks the
constraints on them, and records every annotated declaration / expression
into in-process registries. The IR phase reads those registries, extracts
the raw source text via `IrFileEntry`, normalises it, and rewrites every
`capturedSources<T>()` call into a `listOf(MarkerCtor(...), ...)` literal —
before inline expansion runs, so that expression-level annotations on
inline-function arguments survive.

For the full design, see [docs/architecture.md](docs/architecture.md).

---

## Supported Kotlin versions

| Kotlin | Status | Compat module |
| ------ | ------ | ------------- |
| 2.0.x  | Supported (verified in CI) | `compat-k200` |
| 2.1.x  | Supported (verified in CI) | `compat-k210` |
| 2.2.x  | Not yet verified — falls back to `compat-k210` with a warning | — |
| < 2.0  | **Not supported.** Build fails with a clear message. | — |

The Gradle plugin reads `KotlinGradlePlugin.getKotlinPluginVersion()` from
the consumer project and emits an error below the minimum supported version
or a warning above the highest tested version. The actual dispatch happens
at compile time via `ServiceLoader` inside the bundled compiler plugin JAR.

To add a new minor version locally, see
[docs/adding-kotlin-version-support.md](docs/adding-kotlin-version-support.md).

---

## Constraints

- Kotlin **2.x (K2)** only
- Marker annotations must be **`internal` or `private`** — by design, capture stays confined to a single module
- **KMP support** — JVM / JS / Native / WASM. Marker definitions, use sites, and retrieval all work in `commonMain` and in each platform source set

### Known limitations (excerpt)

The K2 compiler introduces a handful of constraints that the plugin does
not paper over:

1. `@Marker (expr)` requires explicit parentheses on the marker
   (`@Marker() (expr)`).
2. `@Marker () ({ ... })` captures the lambda **without** the outer
   parentheses; prefer `@Marker() run { ... }`.
3. KMP `expect` / `actual` — annotation on the `expect` side is invisible to
   the IR phase. Only the `actual` site is captured.
4. Markers, use sites and `capturedSources<T>()` must live in the same
   Kotlin compilation invocation.
5. Apple-native targets are opt-in via `-PenableAppleTargets=true`.

Full list with reproductions and root-cause analysis:
[docs/known-limitations.md](docs/known-limitations.md).

---

## Documentation

- [docs/architecture.md](docs/architecture.md) — module layout, FIR / IR
  pipeline, and the Logic A – H catalogue.
- [docs/known-limitations.md](docs/known-limitations.md) — K2-imposed
  constraints that the plugin deliberately accepts.
- [docs/adding-kotlin-version-support.md](docs/adding-kotlin-version-support.md)
  — how to add a new `compat-kXYZ` module.
- [compiler-plugin/compat/README.md](compiler-plugin/compat/README.md) —
  compat layer architecture in depth.
- [docs/publishing.md](docs/publishing.md) — Maven Central publishing setup,
  credentials, and release workflow.
- [docs/versioning.md](docs/versioning.md) — semver policy, pre-1.0
  guarantees, release artefacts, and between-release VERSION_NAME handling.


---

## Running the test suite

The project ships with three layers of tests; they run together in CI (`.github/workflows/ci.yml`) and can be run locally with the commands below.

```bash
# Compiler plugin unit tests (FIR / IR extension, via kctfork)
./gradlew :compiler-plugin:test

# Gradle plugin sanity test (ProjectBuilder; DSL wiring only — fast)
./gradlew :gradle-plugin:test

# Gradle plugin E2E (TestKit + fixture project; real `plugins { id(...) }` apply — slower)
./gradlew :integration-test:test-gradle-plugin:test

# JVM-only integration test suite (cases #1 - #100)
./gradlew :integration-test:test-jvm:test

# KMP integration test suite — cases #101 - #105 (run on the JVM target)
./gradlew :integration-test:test-kmp:jvmTest

# Non-JVM KMP targets (compile only by default; mingwX64 cannot run on Unix)
./gradlew :integration-test:test-kmp:linuxX64Test :integration-test:test-kmp:jsTest :integration-test:test-kmp:wasmJsTest

# Compile all non-Apple targets (klib / executables)
./gradlew :integration-test:test-kmp:assemble

# Apple targets (iOS / macOS) — requires Xcode
./gradlew :integration-test:test-kmp:assemble -PenableAppleTargets=true
```

The `:integration-test:test-kmp` module exercises the compiler plugin across `jvm`, `js`, `wasmJs`, `linuxX64`, `mingwX64`, and (opt-in) all Apple native targets. KMP case verification (#101 - #105) runs on the `jvm` target via Kotest; the other targets are covered by per-target sanity checks in their respective `*Test` source sets.

Apple targets are gated behind `-PenableAppleTargets=true` so that contributors without Xcode (and the default CI runner) can still build the project end-to-end. The full Apple test matrix on macOS runners is planned for a later phase.

