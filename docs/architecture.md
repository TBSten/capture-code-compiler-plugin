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
│   ├── compat/                    # Version-agnostic CompatContext interface
│   │                              #   + ServiceLoader-based resolution.
│   ├── compat-k200/               # Kotlin 2.0.x implementation.
│   ├── compat-k210/               # Kotlin 2.1.x implementation.
│   └── src/main/kotlin/.../
│       ├── feature/<feature>/     # Self-contained feature directories
│       │                          #   (capturedsources, expression_annotation,
│       │                          #   …). Features avoid cross-feature
│       │                          #   dependencies.
│       ├── fir/                   # FIR (frontend) extensions.
│       ├── compat/                # Wiring between the main module and the
│       │                          #   :compat / :compat-kXXX siblings.
│       ├── error/                 # Diagnostic factories and message
│       │                          #   rendering. Single source of truth for
│       │                          #   error texts.
│       ├── CaptureCodePluginConfig.kt
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
`compiler-plugin/src/main/kotlin/.../feature/<feature>/`. Examples in the
current tree:

- `feature/capturedsources/` — the `capturedSources<T>()` checker (Logic G)
  and rewriter (Logic H).
- `feature/expression_annotation/` — expression-site detection (Logic B-fir)
  and the IR-side bridge that recovers expression sites from the session
  registry.

Cross-feature shared concerns (Logic A meta-annotation discovery, Logic D
normalization, diagnostic factories) live next to the features under `fir/`,
`error/` and the package root. Features do not depend on each other; they
share state only via the registries (`CaptureCodeMarkerRegistry`,
`CaptureCodeExpressionSiteRegistry`) that the FIR phase populates and the IR
phase consumes.

## Compat layer

Kotlin compiler APIs are unstable across minor versions. The plugin absorbs
that drift through a thin abstraction:

- `:compiler-plugin:compat` declares `CompatContext` — the contract that
  every supported Kotlin version must satisfy (IR transform entry point,
  literal-expression accessors, classifier-symbol helpers, etc.).
- `:compiler-plugin:compat-k200` and `:compiler-plugin:compat-k210` are
  version-specific implementations. Each ships an `@AutoService`-registered
  `CompatContext.Factory` that advertises its `minVersion`.
- The main module ships **all** `compat-k*` modules inside a single ShadowJar
  (`bundled` configuration + `mergeServiceFiles()`).
- At runtime, `CompatContext.Companion.load` walks the available factories
  via `ServiceLoader` and selects the highest `minVersion` that is still
  `<= currentKotlinVersion`.

For the full design of this layer and the algorithm for handling `dev`
tracks, see [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md).
For adding support for a new Kotlin version, see
[adding-kotlin-version-support.md](./adding-kotlin-version-support.md).

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
