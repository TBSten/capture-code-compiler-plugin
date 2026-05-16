# Versioning policy

CaptureCode follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)
(`MAJOR.MINOR.PATCH`), with a small set of project-specific rules that
reflect the unstable nature of the Kotlin compiler API.

The authoritative version string lives in `gradle.properties`
(`VERSION_NAME`). The runtime artifact (`:annotation`), the bundled
compiler plugin JAR (`:compiler-plugin`), and the Gradle plugin
(`:gradle-plugin`) are all released in lockstep with the same version.

## Public API surface

For the purpose of this policy, the **public API** consists of:

- The runtime artifact `me.tbsten.capture.code:annotation`:
  - `@CaptureCode` meta-annotation.
  - Filler types: `Source`, `SourceLocation`, `CaptureKind`.
  - `capturedSources<T>()` retrieval API (signature only — the body is
    rewritten by the compiler plugin).
- The Gradle plugin `me.tbsten.capture.code`:
  - The `captureCode { ... }` DSL block and its options
    (`includeKdoc`, `includeImports`, `includeAnnotationLines`,
    `dedent`, `includeLineInfo`).
  - The plugin id and its automatic wiring of the `:annotation`
    runtime onto `implementation` / `commonMainImplementation`.

Compiler plugin internals (FIR / IR extension classes, compat SPI,
diagnostic factory ids, ...) are **not** part of the public API and may
change in any release.

## Version bump rules

| Change | Bump |
| --- | --- |
| Breaking change to the public API (removal, signature change, semantic change) | `MAJOR` |
| Adding a new supported Kotlin version (e.g. adding a `compat-kXYZ`) | `MINOR` |
| Dropping support for a previously supported Kotlin version | `MAJOR` |
| New filler type, new DSL option, new capture site kind, additive only | `MINOR` |
| Bug fix that does not change the public API | `PATCH` |
| **Unexpected bug fix discovered after a release** (e.g. a regression that escaped CI) | **`PATCH`** |
| Internal refactor without observable behaviour change | `PATCH` |

Adding support for a newer Kotlin version is deliberately a `MINOR`
bump even though it expands the compatibility matrix, because users on
older Kotlin versions are unaffected — the existing `compat-kXYZ`
modules keep working. Dropping a Kotlin version, on the other hand, is
a `MAJOR` bump because it can force users to upgrade their build.

## Pre-1.0 policy (`0.x.y`)

While the project is on `0.x.y`, the public API is **not frozen**:

- Breaking changes can land in a `MINOR` bump (`0.1.x` → `0.2.0`).
  This includes signature changes on filler types, DSL option renames,
  and removal of capture site kinds.
- `PATCH` bumps (`0.1.0` → `0.1.1`) are bug-fix only and never break
  the public API.
- Diagnostic message ids and wording can change without a bump —
  diagnostic stability is a `1.0.0` commitment.

In practice, the `0.x.y` line will move forward conservatively: breaking
changes are called out in the GitHub Release notes for the affected
version, so consumers can plan upgrades. The lack of strict semver
enforcement is an explicit signal that the API is still being shaped.

## 1.0.0 and beyond

`1.0.0` freezes the public API. From `1.0.0` onward, the rules in the
[version bump rules](#version-bump-rules) table are followed strictly:

- `MAJOR` bumps may remove public API surface.
- `MINOR` bumps are additive only on the public API surface.
- `PATCH` bumps are bug-fix only.

A `1.0.0` release will be cut once:

1. The five Known Limitations in [docs/known-limitations.md](known-limitations.md)
   are either resolved or explicitly accepted as permanent.
2. The supported Kotlin version matrix has been stable for at least one
   full Kotlin minor cycle.
3. At least one `0.x` line has shipped to Maven Central and seen real
   user feedback.

## Release artefacts and tags

- Git tag: `vX.Y.Z` (e.g. `v0.1.0`).
- Maven Central artifacts:
  - `me.tbsten.capture.code:annotation:X.Y.Z` (KMP).
  - `me.tbsten.capture.code:compiler-plugin:X.Y.Z` (shadowed,
    bundles `compat-*`).
  - `me.tbsten.capture.code:gradle-plugin:X.Y.Z` (Gradle plugin
    marker).
- GitHub release: the body is auto-generated from commit log via
  `softprops/action-gh-release@v2` (`generate_release_notes: true`).

The actual release process (signing, staging, promotion) is documented
in [docs/publishing.md](publishing.md).

## Between releases

`-SNAPSHOT` suffix を **使わない** 方針。 main branch の
`gradle.properties:VERSION_NAME` は **常に「次にリリースする予定の version」**
を素の `MAJOR.MINOR.PATCH` 形式で保持する:

- 通常の機能追加サイクル中: `0.2.0` (次の MINOR 予定)
- 直近 release に対して unexpected bug fix が必要になったとき: `0.1.2`
  などの PATCH に書き換えてから release

これにより:
- `publishToMavenLocal` 等で生成される artifact が常に意味ある version 文字列
  を持つ (`-SNAPSHOT` がついた中途半端な artifact が混ざらない)
- 「unexpected bug fix = patch release」 のルールが build script ではなく
  `VERSION_NAME` の数字そのもので表現される

> 注意: `-SNAPSHOT` を外している分、 ローカルでの誤 `publishAndReleaseToMavenCentral`
> が即 release につながる可能性がある。 本 repo では release を **CI tag push
> trigger** に限定 (`release.yml`) しているため、 ローカル credentials を持たない
> 通常開発者には影響しないが、 publishing credentials を設定する場合は
> `publishToMavenLocal` 以外を慎重に扱うこと。

## Release history

This section captures per-release deltas that warrant a heads-up beyond the
auto-generated GitHub Release notes. Strict policy is in
[Version bump rules](#version-bump-rules); this is the human-readable log.

### 0.2.0

**Theme**: internal restructure following the Compatibility Layer pattern
(tasks 116 – 125). No public API change.

Breaking changes (user-visible, pre-1.0 so packaged as a `MINOR` bump per
the [Pre-1.0 policy](#pre-10-policy-0xy)):

- **Diagnostics are now English-only.** The `CAPTURECODE_LOCALE` env var
  is silently ignored. The Japanese variants of compiler diagnostics from
  the 0.1.x line are no longer produced. The English texts are the
  single source of truth, defined per logic in
  `compiler-plugin/src/main/.../feature/<feature>/.../<Logic>Errors.kt`
  and `<Logic>Warnings.kt`. (task-122)
- **Diagnostic message ids and wording were re-aligned.** Diagnostic
  stability is a `1.0.0` commitment (see [Pre-1.0 policy](#pre-10-policy-0xy));
  callers that pattern-match on diagnostic text are still considered
  out-of-policy, but the post-restructure wording is now stable across
  every supported Kotlin baseline.
- **Internal package layout was refactored.** Domain logic that lived
  under `:compiler-plugin:compat` in 0.1.x now lives in the main
  `:compiler-plugin` module under `src/main/kotlin/.../feature/`. The
  public API surface (`@CaptureCode`, the filler types, the Gradle DSL)
  is unchanged.

Compatibility notes:

- **Adding a new Kotlin version still requires a new `compat-kXXX`
  module.** The IR walker / rewriter / filler / userargs remain inside
  each `compat-kXXX/` to absorb IR drift D5–D8 (task-120-B case C).
  Adding a baseline therefore creates a new module with ~12–13 Kotlin
  files (plus 0–5 Java shims on 2.2.x+ for FIR Checker `check(...)`
  argument-order drift). See
  [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md)
  for the full checklist.
- **Supported Kotlin baselines**: 2.0.0, 2.0.10–2.0.21, 2.1.x, 2.2.x,
  2.3.x, 2.4.0-RC{,N}.

### 0.1.x

See the GitHub Release notes for individual `0.1.0` / `0.1.1` / `0.1.2`
entries. No `MAJOR` changes since the project started shipping.
