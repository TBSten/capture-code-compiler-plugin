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

Each supported Kotlin version has its own module:

- [`compat-k200/`](../compat-k200) — Kotlin 2.0.0 (baseline).
- [`compat-k202/`](../compat-k202) — Kotlin 2.0.10 .. 2.0.21 (absorbs drift
  D8 around `IrVarargImpl` constructor changes etc.).
- [`compat-k210/`](../compat-k210) — Kotlin 2.1.x.
- [`compat-k220/`](../compat-k220) — Kotlin 2.2.x (introduces Java shims in
  `src/main/java/.../checker/K220*Shim.java` to absorb FIR Checker
  `check(...)` argument-order drift D9).
- [`compat-k230/`](../compat-k230) — Kotlin 2.3.x (adds
  `K230RendererMapShim.java`; the nested `K230Diagnostics.MAP` is `by lazy`
  to avoid static-init NPE).
- [`compat-k240rc/`](../compat-k240rc) — Kotlin 2.4.0-RC{,N}.

### Slim-down outcome (task-120-B case C, task-124)

After the domain SSoT migration (task-118 onwards), each `compat-kXXX/`
module retains roughly:

- **12–13 Kotlin files** covering `CompatContextImpl` (with nested
  `K{XXX}Diagnostics`), `K{XXX}IrTransform`, `K{XXX}CapturedSourcesCollector`,
  `K{XXX}CapturedSourcesRewriter`, `SourceTextExtractor`,
  `checker/K{XXX}CheckerExtensions`, `filler/*`, `userargs/*`.
- **0–5 Java shim files** under `src/main/java/.../checker/` from
  Kotlin 2.2.x onwards, for FIR Checker `check(...)` argument-order
  drift D9 and for `RendererMap` static-init drift in 2.3+.

The IR walker / rewriter / filler / userargs were **not** migrated to the
main module: drift D5–D8 (IR builder / `IrConstructorCall` /
`IrConst` / `IrFactory` shape changes) is still live and pulling the IR
phase out would have required reintroducing the same per-version shimming
under a different name. Keeping the IR logic inside each `compat-kXXX`
module — the "case C" outcome of task-120-B — leaves the slim form above.

Each module contains:

- `CompatContextImpl` — version-specific implementation, with a nested
  `K{XXX}Diagnostics` object holding the per-id `KtDiagnosticFactory*` map
  consumed via `CompatContext.diagnosticFactory(id)`.
- An inner `Factory : CompatContext.Factory` that declares the module's
  `minVersion`.
- AutoService-generated service loader configuration
  (`META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory`)
  via `@AutoService(CompatContext.Factory::class)` + KSP.
- From Kotlin 2.2.x: Java shim classes under `src/main/java/.../checker/`
  that extend the matching Kotlin checker class and forward `check(...)`
  with the argument order required by that Kotlin baseline.

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
6. If the new baseline drifts a method signature on a FIR Checker base
   class (e.g. `check(...)` argument order in 2.2.x), add a Java shim
   under `src/main/java/.../compat/k<XXX>/checker/K<XXX>*Shim.java` that
   extends the corresponding Kotlin checker class and forwards `check(...)`
   with the argument order required by the new baseline. The K220 / K230 /
   K240Rc shims in the existing `compat-k220` / `compat-k230` /
   `compat-k240rc` modules serve as templates.
7. If the new baseline changes a renderer / static-init shape (e.g.
   `KtDiagnosticFactoryToRendererMap` super-class swap in 2.3+), make the
   nested `K<XXX>Diagnostics.MAP` `by lazy` so the renderer chain does not
   NPE during class init. See `K230RendererMapShim.java` for the K230 / K240Rc
   template.

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
