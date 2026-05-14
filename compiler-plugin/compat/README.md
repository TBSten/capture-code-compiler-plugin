# Capture Code Compiler Compatibility Layer

This module provides a compatibility layer for Capture-Code's compiler plugin
to work across different Kotlin compiler versions. As the Kotlin compiler APIs
evolve and change between versions, this layer abstracts away version-specific
differences.

This collection of artifacts is not published and is instead shaded into
Capture-Code's compiler plugin (via the `bundled` configuration in
`compiler-plugin/build.gradle.kts` + `shadowJar.mergeServiceFiles()`).

Adapted from
[Metro's `compiler-compat`](https://github.com/ZacSweers/metro) (Apache 2.0)
and [compose-preview-lab's port](https://github.com/tbsten/compose-preview-lab).

## Overview

The Kotlin compiler plugin APIs are not stable and can change between
versions. Some APIs get deprecated, renamed, or removed entirely.
This compatibility layer provides a uniform interface
([`CompatContext`](src/main/kotlin/me/tbsten/capture/code/compat/CompatContext.kt))
that the Capture-Code compiler plugin can use regardless of the underlying
Kotlin version.

## Architecture

### Core Interface

The [`CompatContext`](src/main/kotlin/me/tbsten/capture/code/compat/CompatContext.kt)
interface defines the contract for version-specific operations:

- `transformIr(...)` — the IR transformation entry point (absorbs IR-side
  drift D5–D8 between supported Kotlin versions).
- `literalValueOrNull(expression)` / `isLiteralExpression(expression)` —
  absorbs the `FirLiteralExpression<T>` → `FirLiteralExpression` type-parameter
  removal in Kotlin 2.0.21+ (FIR drift D1).
- `toRegularClassSymbolOrNull(type, session)` — absorbs the
  `toRegularClassSymbol` extension's package move from `fir.types` (2.0.x) to
  `fir.resolve` (2.1.x) (FIR drift D2).
- `classIdOf(symbol)` — guards `FirRegularClassSymbol.classId` accessor
  drift (D3).

### Version-Specific Implementations

Each supported Kotlin version has its own module with a corresponding
implementation:

- [`compat-k200/`](../compat-k200) — Kotlin 2.0.x compatibility.
- [`compat-k210/`](../compat-k210) — Kotlin 2.1.x compatibility (FIR drift is
  fully absorbed; IR drift D5–D8 absorption is tracked as ongoing work).

Each module contains:

- `CompatContextImpl` — version-specific implementation.
- An inner `Factory : CompatContext.Factory` that declares the
  module's `minVersion`.
- AutoService-generated service loader configuration
  (`META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory`)
  via `@AutoService(CompatContext.Factory::class)` + KSP.

### Service Discovery

The compatibility layer uses Java's `ServiceLoader` mechanism to discover
available implementations at runtime. The shadowed plugin jar bundles
**every** `compat-k*` module (see `compiler-plugin/build.gradle.kts`'s
`bundled` configuration + `tasks.shadowJar { mergeServiceFiles() }`), and
[`CompatContext.Companion.load`](src/main/kotlin/me/tbsten/capture/code/compat/CompatContext.kt)
picks the best one at runtime based on the available Kotlin version.

## Adding Support for New Kotlin Versions

### Automatic Generation

Use the provided script to generate a skeleton for a new Kotlin version:

```bash
cd <repo-root>
./compiler-plugin/compat/generate-compat-module.sh 2.2.0
```

This will create:

- Module directory structure (`compiler-plugin/compat-k220/`).
- `build.gradle.kts` (compileOnly against the matching
  `kotlin-compiler-embeddable-k220` library).
- Skeleton `CompatContextImpl.kt` with `TODO()` calls for every interface
  method.
- AutoService-decorated `Factory` declaring the new `minVersion`.

After generation, you still need to:

1. Add a `kotlin-k220 = "2.2.0"` line to `gradle/libs.versions.toml`.
2. Add a `kotlin-compiler-embeddable-k220` entry pointing at it.
3. Add the new module to `settings.gradle.kts`
   (`include(":compiler-plugin:compat-k220")`).
4. Add it to `compiler-plugin/build.gradle.kts`'s `bundled` and
   `testImplementation` blocks.
5. Replace the `TODO()`s in `CompatContextImpl.kt` with actual
   implementations.

### Version Naming Convention

The script converts Kotlin versions to short, lowercase package suffixes.
This project uses **3-digit** suffixes (Kotlin patch levels collapse into the
minor track):

- `2.0.0` → `k200`
- `2.0.21` → `k200` (same minor; patch differences are handled at the
  Factory.minVersion level)
- `2.1.0` → `k210`
- `2.2.0` → `k220`
- `2.4.0-Beta1` → `k240_beta1`
- `2.5.0-dev-1234` → `k250_dev_1234`

(This is a different convention than Metro/compose-preview-lab, which use
4-digit suffixes such as `k2320`. Capture-Code projects only need the
minor-level granularity for now.)

## Runtime Selection

Capture-Code's compiler plugin uses `ServiceLoader` to discover and select
the appropriate compatibility implementation at runtime. The dispatch logic
lives in
[`CompatContext.Companion.resolveFactory`](src/main/kotlin/me/tbsten/capture/code/compat/CompatContext.kt).

This allows Capture-Code to support multiple Kotlin versions without
requiring separate builds.

### Selection Algorithm

Among all `Factory` implementations whose `minVersion <= currentVersion`,
pick the one with the largest `minVersion`. For example, when running on
Kotlin 2.1.5 with factories for 2.0.0 and 2.1.0 registered, the 2.1.0
implementation wins.

### Track-Based Resolution

`dev` track versions (e.g., `2.3.20-dev-5706`) are handled specially to avoid
issues with divergent release tracks.

Kotlin's release process can create divergent version tracks:

- **dev builds** are from the main development branch (trunk).
- **Beta/RC builds** are cut from stable branches with different changes.

For example:

- `2.3.20-dev-5706` — has API change X.
- `2.3.20-Beta1` — released from a branch, has API change X + Y.
- `2.3.20-dev-7791` — new dev build, has X + Z (not Y from Beta1).

Standard semantic version comparison would incorrectly say
`2.3.20-dev-7791 < 2.3.20-Beta1` (because `DEV < BETA` in maturity ordering),
potentially selecting the wrong factory.

The resolution logic handles this by:

1. If the current version is a dev build, first look for dev track factories
   only.
2. Compare only within the dev track (by build number).
3. If no dev factory matches, fall back to non-dev factories using the base
   version stripped of the dev classifier.

This ensures dev builds use dev-specific factories when available, and
Beta/RC/Stable versions never accidentally use dev factories.

## Development Notes

- Always implement all interface methods, even if some are no-ops for
  certain versions.
- Include KDoc explaining version-specific behavior (see `CompatContextImpl`
  in each `compat-k*` module for examples).
- Test thoroughly with the target Kotlin version before declaring support.
  The repo's CI matrix exercises every supported Kotlin version against each
  compat module to catch drift early.
- Keep implementations focused and minimal — avoid adding version-specific
  extensions beyond the interface contract. New drift points should be added
  to `CompatContext` itself, not implemented as one-off helpers.
