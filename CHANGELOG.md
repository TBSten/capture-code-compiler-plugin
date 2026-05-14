# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
with the pre-1.0 policy described in [docs/versioning.md](docs/versioning.md).

## [Unreleased]

Nothing yet.

## [0.1.0] - 2026-05-14

Initial public release of the CaptureCode compiler plugin. The plugin
captures the source string of declarations / expressions / files marked
with a user-defined `@CaptureCode` annotation at compile time, and lets
the runtime read them back through `capturedSources<T>()` with zero
reflection and zero runtime cost.

### Added

- **`@CaptureCode` meta-annotation API** — mark any user annotation with
  `@CaptureCode` to turn it into a capture marker. The runtime stub lives
  in the `:annotation` artifact (`me.tbsten.capture.code:annotation`).
- **`capturedSources<T>()` retrieval API** — returns every annotation
  instance whose marker is `T`, populated with the captured source string
  and optional filler values. The call is rewritten to a constant
  `listOf(...)` literal at compile time.
- **Capture sites across all declaration targets** — property, class,
  object, function, and typealias declarations can be marked directly
  with the user-defined marker.
- **File-level capture (`@file:Marker`)** — annotate the file header to
  capture the whole file body as a single source string.
- **Expression-level capture** — write `@Marker() (expr)` or
  `@Marker() run { ... }` to capture an inline expression. The capture
  happens before inline expansion, so expressions passed to inline
  functions also survive.
- **Filler types** — declare any of the following parameter types on the
  marker annotation and the plugin will fill in the value automatically.
  Omitting them keeps the capture opt-in:
  - `Source(val value: String)` — captured source string.
  - `SourceLocation(packageName, filePath, startLine, endLine)` — capture
    site location.
  - `CaptureKind(val value: Kind)` — `EXPRESSION` / `PROPERTY` / `CLASS`
    / `OBJECT` / `FUNCTION` / `TYPEALIAS` / `FILE`.
- **User-defined parameters preserved** — any non-filler parameter on the
  marker (e.g. `id: String`, `method: HttpMethod`) keeps the literal
  value supplied at the use site, including primitives, `String`,
  enums, `KClass`, nested annotations, and arrays of them.
- **Marker constraints checker** — diagnoses missing or wrong
  configuration on the marker definition itself (visibility,
  `@Retention`, `@Target`, filler default values, ...).
- **`capturedSources<T>()` constraints checker** — diagnoses misuse of
  the retrieval API at call sites (missing type argument, non-marker
  type, ...).
- **Source normalisation** — dedent, blank-line trimming, and stripping
  of package / import lines so that the captured string is suitable for
  display and documentation use cases.
- **Gradle plugin DSL** — `captureCode { ... }` block with five options:
  - `includeKdoc` (default `true`).
  - `includeImports` (default `false`, only affects `@file:` captures).
  - `includeAnnotationLines` (default `false`).
  - `dedent` (default `true`).
  - `includeLineInfo` (default `true`).
- **Kotlin Multiplatform support** — JVM / JS / WASM (`wasmJs`) /
  Linux (`linuxX64`) / Windows (`mingwX64`) / Apple native targets.
  Apple targets are gated behind `-PenableAppleTargets=true` so that
  contributors without Xcode can still build the project end-to-end.
- **Multi-Kotlin version support** — Kotlin 2.0.x and 2.1.x are verified
  in CI through a `compat` SPI layer (`compat-k200` / `compat-k210`)
  inspired by the Metro pattern. The bundled compiler plugin JAR picks
  the right implementation at runtime via `ServiceLoader`.
- **Minimum Kotlin version guard** — the Gradle plugin reads the
  consumer project's Kotlin version and fails fast below the minimum
  supported version, or warns above the highest tested one.
- **Bilingual diagnostic messages** — every diagnostic is rendered with
  both an English explanation and a Japanese one, so contributors on
  either side can read the error without context switching.
- **Maven Central publishing wiring** — the `:annotation`,
  `:compiler-plugin` (shadow-bundled with all `compat-*` modules), and
  `:gradle-plugin` artifacts are wired through the vanniktech publish
  convention plugin. `gradle.properties` is the single source of truth
  for `VERSION_NAME` and `GROUP`.

### Known Limitations

The following constraints come from the Kotlin 2.x K2 compiler itself
and the plugin deliberately does not paper over them. See
[docs/known-limitations.md](docs/known-limitations.md) for reproducions
and root-cause analysis.

- `@Marker (expr)` requires explicit parentheses on the marker
  (`@Marker() (expr)`); without them the K2 parser consumes the
  expression as constructor arguments.
- `@Marker () ({ ... })` strips the outer parentheses from lambda
  capture sites. Prefer `@Marker() run { ... }`.
- In KMP `expect` / `actual`, annotations on the `expect` declaration
  are dropped by K2 IR. Only the `actual` site is captured.
- Marker definitions, use sites, and `capturedSources<T>()` calls must
  live in the same Kotlin compilation invocation. For KMP tests this
  means placing all three in the test source-set tree.
- Apple-native targets are opt-in via `-PenableAppleTargets=true`
  because they require a local Xcode installation.

[Unreleased]: https://github.com/tbsten/capture-code-compiler-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/tbsten/capture-code-compiler-plugin/releases/tag/v0.1.0
