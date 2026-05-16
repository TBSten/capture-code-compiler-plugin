# Architecture Overview

This document describes how the CaptureCode compiler plugin is organized
internally and how a Kotlin source file flows through the plugin during
compilation. For the user-facing API and quick-start examples, see
[README.MD](../README.MD) / [README.ja.md](../README.ja.md).

## Module layout

```
capture-code-compiler-plugin/
├── annotation/                    # Kotlin Multiplatform runtime stub
│                                  #   @CaptureCode meta-annotation, the
│                                  #   filler types (Source / SourceLocation /
│                                  #   CaptureKind) and the capturedSources<T>()
│                                  #   stub that the IR phase rewrites.
│
├── compiler-plugin/               # The compiler plugin proper.
│   ├── compat/                    # Version-agnostic CompatContext SPI.
│   │                              #   Holds only the contract, the
│   │                              #   ServiceLoader-backed holder, and the
│   │                              #   plugin-config data class. No domain
│   │                              #   knowledge (FQNs, normalization,
│   │                              #   diagnostic strings) lives here.
│   ├── compat-k200/               # Kotlin 2.0.x implementation.
│   ├── compat-k202/               # Kotlin 2.0.10..2.0.21 implementation.
│   ├── compat-k210/               # Kotlin 2.1.x implementation.
│   ├── compat-k220/               # Kotlin 2.2.x implementation.
│   ├── compat-k230/               # Kotlin 2.3.x implementation.
│   ├── compat-k240rc/             # Kotlin 2.4.0-RC{,N} implementation.
│   └── src/main/kotlin/.../       # Main module — Kotlin 2.0.0 baseline.
│       ├── feature/markerDefinition/    # Marker meta-annotation domain
│       │                                #   (Logic A discovery, Logic F
│       │                                #   marker-annotation validation,
│       │                                #   duplicate / unused param warnings).
│       ├── feature/capturedSources/     # capturedSources<T>() pipeline
│       │                                #   (Logic G call validation, Logic B
│       │                                #   declaration / expression-site
│       │                                #   collection, Logic C source-text
│       │                                #   extraction, Logic D normalization,
│       │                                #   Logic H rewrite, marker-not-found
│       │                                #   warning).
│       ├── error/                       # Plugin-wide structured Error SPI
│       │                                #   (interface, DSL, ReportError).
│       │                                #   Diagnostic message texts live next
│       │                                #   to each logic, in <Logic>Errors.kt.
│       ├── warning/                     # Plugin-wide structured Warning SPI
│       │                                #   (interface, DSL, ReportWarning).
│       │                                #   Warning texts live in
│       │                                #   <Logic>Warnings.kt next to each
│       │                                #   logic.
│       ├── CaptureCodeCommandLineProcessor.kt
│       ├── CaptureCodeCompilerPluginRegistrar.kt
│       ├── CaptureCodeFirExtensionRegistrar.kt
│       └── CaptureCodeIrExtension.kt
│
├── gradle-plugin/                 # KotlinCompilerPluginSupportPlugin
│                                  #   implementation. Detects the user's
│                                  #   Kotlin version, registers DSL options
│                                  #   and adds the :annotation runtime
│                                  #   dependency to the consumer project.
│
└── integration-test/              # End-to-end coverage.
    ├── test-jvm/                  # JVM-only cases (#1 – #100, kctfork).
    ├── test-kmp/                  # KMP cases (#101 – #105) across jvm /
    │                              #   js / wasmJs / native targets.
    └── test-gradle-plugin/        # Gradle TestKit + fixture project that
                                   #   applies `plugins { id(…) }` for real.
```

The same layout is reflected in `settings.gradle.kts`.

## Compilation pipeline

The plugin participates in two compiler phases. Each call to the Kotlin
compiler executes them in order.

```
                ┌───────────────────────────────────────────────┐
                │ FIR phase                                     │
                │                                               │
   .kt files ──▶│  A. Meta-annotation discovery                 │
                │      └── CaptureCodeFirMarkerService          │
                │  F. Marker annotation checker                 │
                │      └── visibility / @Retention / @Target /  │
                │          parameter-type / filler-default      │
                │  G. capturedSources<T>() checker              │
                │      └── verifies @CaptureCode on T           │
                │  B-fir. Expression-site collector             │
                │      └── pushes (file, offset, marker, args)  │
                │          into CaptureCodeExpressionSiteRegistry│
                └───────────────────────────────────────────────┘
                                       │
                                       ▼
                ┌───────────────────────────────────────────────┐
                │ IR phase (early lowering)                     │
                │                                               │
                │  CaptureCodeIrExtension                       │
                │      └── delegates to CompatContext.transformIr│
                │  B-ir. Declaration-site collection            │
                │  C.    Source text retrieval (IrFileEntry,    │
                │        PSI fallback)                          │
                │  D.    Source normalization (dedent, trims)   │
                │  H.    capturedSources<T>() rewrite           │
                │        └── replaces the IrCall with           │
                │            listOf(MarkerCtor(...), …)         │
                └───────────────────────────────────────────────┘
                                       │
                                       ▼
                                 .class / .klib
```

The IR transform runs **before inline expansion** so that expression-level
annotations on inline-function arguments (e.g. `@Marker run { … }`) are still
addressable.

## Feature directory convention

Each user-facing feature lives in its own directory under
`compiler-plugin/src/main/kotlin/.../feature/<feature>/`. Two features
exist in the current tree:

- `feature/markerDefinition/` — domain of the `@CaptureCode` meta-annotation
  itself. Logic A (marker discovery), Logic F (marker-annotation validation:
  `expect`, parameter types, filler defaults, …), and the duplicate / unused
  parameter warnings emitted from the IR phase.
- `feature/capturedSources/` — the `capturedSources<T>()` pipeline.
  Logic G (call validation), Logic B-fir (expression-site collection),
  Logic B-ir (declaration-site collection), Logic C (source-text retrieval
  via `IrFileEntry` + PSI fallback), Logic D (normalization chain:
  `Dedent` / `KdocStrip` / `BlankTrim` / `AnnotationLineStrip` /
  `PackageImportStrip`), and Logic H (the IR rewrite that replaces the call
  with `listOf(MarkerCtor(...), …)`).

Features do not depend on each other. They share state only via the
compilation-scoped registries
(`CaptureCodeMarkerRegistry`, `CaptureCodeExpressionSiteRegistry`)
that the FIR phase populates and the IR phase consumes. `CaptureCodeIrExtension`
calls `reset()` on both registries in its `finally` block so consecutive
compiles (e.g. inside `kctfork`) cannot leak state.

The convention `feature/<feature>/<phase>/<logic>/` (e.g.
`feature/capturedSources/fir/validateCapturedSourcesCall/`,
`feature/capturedSources/ir/normalize/`) lets each logic own its diagnostic
text SSoT (`<Logic>Errors.kt` / `<Logic>Warnings.kt`) next to the code that
emits it.

## Compat layer

Kotlin compiler APIs are unstable across minor — sometimes across patch —
versions. The plugin absorbs that drift through a single SPI plus a small
fleet of version-specific implementations:

- `:compiler-plugin:compat` declares the `CompatContext` SPI plus the
  `CaptureCodeCompatHolder` lazy holder and the `CaptureCodePluginConfig`
  data class. **No domain knowledge** lives here.
- `:compiler-plugin:compat-k200` (Kotlin 2.0.0), `compat-k202` (2.0.10..2.0.21),
  `compat-k210` (2.1.x), `compat-k220` (2.2.x), `compat-k230` (2.3.x), and
  `compat-k240rc` (2.4.0-RC{,N}) are the version-specific implementations.
  Each ships an `@AutoService(CompatContext.Factory::class)` whose
  `minVersion` advertises the lowest Kotlin version it covers.
- The main module ships **all** `compat-k*` modules inside a single ShadowJar
  (`bundled` configuration + `tasks.shadowJar { mergeServiceFiles() }`).
- At runtime, `CompatContext.Companion.load` walks the available factories
  via `ServiceLoader` and selects the highest `minVersion <= currentKotlinVersion`.
  Dev / Beta / RC track resolution is documented in
  [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md).

### `CompatContext` surface

The SPI is **low-level on purpose** (option 2 in the task-117 decision): each
known drift point is exposed as one method so the main module can keep its
2.0.0 baseline bytecode while still working on later runtimes. The current
methods are:

- `transformIr(moduleFragment, pluginContext, config)` — the IR phase entry
  point. The walker / rewriter / filler / userargs that produce the
  `listOf(MarkerCtor(...), …)` replacement live inside each compat module
  (see "IR drift retention" below).
- `firAdditionalCheckersExtensions()` — the FIR phase entry point. Returns
  per-`FirSession` factories that the main `CaptureCodeFirExtensionRegistrar`
  registers via `+::`.
- `registerExtensions(extensionStorage, ...)` — absorbs drift D10
  (`CompilerPluginRegistrar.ExtensionStorage.registerExtension(...)` signature
  change between 2.2.x and 2.3.x).
- `literalValueOrNull(expr)` / `isLiteralExpression(expr)` — drift D1
  (`FirLiteralExpression<T>` type parameter removal in 2.0.21+).
- `toRegularClassSymbolOrNull(type, session)` — drift D2 (`fir.types` →
  `fir.resolve` move in 2.1.x).
- `classIdOf(symbol)` — guards drift D3.
- `containingFilePathOf(checkerContext)` — drift D12 (the `containingFile`
  accessor on `CheckerContext` was removed in 2.3.x; flattened to
  `containingFilePath`). Added in task-119.
- `fullyExpandedTypeOf(type, session)` — drift D11 (the 2-arg
  `fullyExpandedType` overload was removed in 2.0.20+). Added in task-119.
- `loadFileText(file)` — wraps PSI / IrFileEntry source-text retrieval for
  main-module IR logic that cannot directly hold a PSI reference compiled
  against the 2.0.0 baseline. Added in task-120.
- `diagnosticFactory(id)` — looks the id (`CC_<feature>_<rule>`) up in each
  compat module's nested `K{XXX}Diagnostics`, returning the
  `KtDiagnosticFactory0` / `KtDiagnosticFactory1<*>`. The main module's
  `ReportError` / `ReportWarning` helpers narrow the result before calling
  `reporter.reportOn(...)`. Added in task-121.

### `mainClassesOnly` outgoing configuration (task-118)

Each `compat-kXXX/` module compiles against its own
`kotlin-compiler-embeddable-k<XXX>` baseline, but it must also `compileOnly`
the main module's domain SSoT (callable ids, normalize chain, English-only
`<Logic>Errors.kt` / `<Logic>Warnings.kt`). Resolving the standard
`apiElements` of `:compiler-plugin` would route through the shadow JAR
(which itself depends on every `compat-kXXX`), creating a dependency cycle.

`compiler-plugin/build.gradle.kts` therefore exposes a dedicated outgoing
variant, `mainClassesOnly`, whose only artifact is the `compileKotlin`
output directory. Each compat module's `build.gradle.kts` consumes this
variant via attribute matching (`Usage.JAVA_API`,
`LibraryElements.CLASSES`), so the compat compile classpath sees the main
module's `.class` files directly without re-entering the shadow JAR. A
parallel `mainRuntimeClassesOnly` variant (`Usage.JAVA_RUNTIME`,
`LibraryElements.JAR`) feeds the compat modules' `testRuntimeOnly`
configurations, because the nested `K{XXX}Diagnostics` objects read
`<Logic>Errors.kt` `.message` strings at `<clinit>` time.

### IR drift retention (task-120-B case C)

Migrating the IR walker / rewriter / filler / userargs **into** the main
module was investigated in task-120 (the FIR-side moved in task-119 without
trouble). The IR phase, however, depends on `IrBuilder` /
`IrConstructorCall` / `IrConst` / `IrFactory` shapes that drift more
aggressively (D5–D8 are still live), so keeping the IR logic inside each
`compat-kXXX` module ("case C") was the chosen outcome. The visible effect
is that each compat module today contains ~12–13 Kotlin files (plus 0–5
Java shims on 2.2.x+ for FIR Checker signature drift D9) covering the IR
phase, while the FIR phase is shared via the main module.

### Adding a new Kotlin version

See [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md)
("Adding Support for New Kotlin Versions") and
[adding-kotlin-version-support.md](./adding-kotlin-version-support.md) for
the full checklist (`generate-compat-module.sh` skeleton →
`gradle/libs.versions.toml` entry → `settings.gradle.kts` include →
`compiler-plugin/build.gradle.kts` `bundled` + `testImplementation`).

## Gradle plugin responsibilities

`gradle-plugin/` is a `KotlinCompilerPluginSupportPlugin`. Its only jobs are:

1. **Version guard** (`CaptureCodeGradlePlugin.checkKotlinVersionOrFail`).
   Hard-fail if the project uses Kotlin below `MIN_SUPPORTED_VERSION`; warn
   if it uses a Kotlin newer than `MAX_TESTED_VERSION_EXCLUSIVE`.
2. **Runtime dependency wiring**. Adds `:annotation` to either
   `commonMainImplementation` (KMP) or `implementation` (JVM) automatically
   in `afterEvaluate`.
3. **DSL → SubpluginOption translation**. Maps the `captureCode { … }` block
   to the five `SubpluginOption`s consumed by
   `CaptureCodeCommandLineProcessor`.

The actual selection of a version-specific compat implementation is **not**
the Gradle plugin's responsibility — that happens inside the compiler plugin
JAR via `ServiceLoader`. This keeps the Gradle plugin classpath small and
avoids depending on `kotlin-compiler-embeddable` at the Gradle level.

## Logic catalogue (A – H)

The logical units below correspond to the labels used in commit messages,
ticket descriptions and internal design notes.

| Logic | Phase | Responsibility                                           |
| ----- | ----- | -------------------------------------------------------- |
| A     | FIR   | Collect every `@CaptureCode`-meta-annotated class.       |
| B     | FIR + IR | Find every annotated declaration / expression site.   |
| C     | IR    | Retrieve raw source text via `IrFileEntry` (PSI fallback). |
| D     | IR    | Normalize source text (dedent, blank-line trim, etc.).   |
| F     | FIR   | Check marker annotation constraints.                     |
| G     | FIR   | Check `capturedSources<T>()` call sites.                 |
| H     | IR    | Rewrite `capturedSources<T>()` into a `listOf(…)` literal. |
| I     | Gradle| `KotlinCompilerPluginSupportPlugin` + DSL options.      |

## Further reading

- [Known limitations](./known-limitations.md) — the constraints inherited
  from the K2 parser / IR pipeline that the plugin deliberately does not
  paper over.
- [Adding Kotlin version support](./adding-kotlin-version-support.md) —
  how to add a new `compat-kXXX` module when a new Kotlin minor is released.
- [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md) —
  the compat layer design in depth, including the `dev`-track resolution
  algorithm.
