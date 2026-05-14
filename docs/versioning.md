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

## SNAPSHOT versions

Between releases the version in `gradle.properties` carries the
`-SNAPSHOT` suffix (e.g. `0.1.1-SNAPSHOT`). Snapshots are published to
the Maven Central snapshot repository when the release workflow runs on
a non-tag push. The `-SNAPSHOT` suffix is detected in each publishable
module's `mavenPublishing { ... }` block via
`endsWith("-SNAPSHOT")` to gate `automaticRelease`.

For the release / SNAPSHOT bump procedure, see the **Release procedure**
section of [docs/publishing.md](publishing.md).
