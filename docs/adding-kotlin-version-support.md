# Adding Support for a New Kotlin Version

CaptureCode follows the **Metro pattern**: a tiny version-agnostic
`CompatContext` interface lives in `:compiler-plugin:compat`, and each
supported Kotlin minor has a sibling `:compiler-plugin:compat-kXYZ` module
that implements it against that version's `kotlin-compiler-embeddable`.
All `compat-k*` modules are bundled into the published compiler plugin JAR
via ShadowJar, and the right implementation is picked at runtime through
`ServiceLoader`.

This page walks through what to do when a new Kotlin minor (e.g. `2.2.0`)
comes out. For the architectural background, see
[architecture.md](./architecture.md) and
[../compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md).

---

## 1. Generate a new compat module skeleton

From the repository root:

```bash
./compiler-plugin/compat/generate-compat-module.sh 2.2.0
```

This creates:

- `compiler-plugin/compat-k220/` (directory + `build.gradle.kts`).
- `CompatContextImpl.kt` with a `TODO()` for every interface method.
- An `@AutoService(CompatContext.Factory::class)`-annotated inner `Factory`
  declaring `minVersion = "2.2.0"`.

The script follows the **3-digit naming convention** used in this repo:
`2.0.0 → k200`, `2.0.21 → k200`, `2.1.0 → k210`, `2.2.0 → k220`,
`2.4.0-Beta1 → k240_beta1`, `2.5.0-dev-1234 → k250_dev_1234`. Patch-level
differences are absorbed inside the same module via the `Factory.minVersion`
field.

## 2. Wire the new module into the build

The script does not (yet) edit shared files. Do the following manually:

1. **`gradle/libs.versions.toml`** — add the new Kotlin version and a
   matching `kotlin-compiler-embeddable-k220` dependency entry. Existing
   `*-k200` / `*-k210` lines are the template.
2. **`settings.gradle.kts`** — add
   `include(":compiler-plugin:compat-k220")`.
3. **`compiler-plugin/build.gradle.kts`** — add the new module to the
   `bundled` configuration (so ShadowJar ships its classes + service file)
   **and** to `testImplementation` (so unit tests can resolve it).
4. **`gradle-plugin/src/main/kotlin/.../SupportedKotlinVersions.kt`** — bump
   `MAX_TESTED_VERSION_EXCLUSIVE` to the version **after** the one you just
   added (e.g. `"2.3.0"`). This silences the “untested version” warning for
   the new minor.

## 3. Fill in the CompatContext implementation

Open `compat-k220/src/main/kotlin/.../CompatContextImpl.kt` and replace every
`TODO()` with the version-appropriate call. The most useful reference is the
`compat-k210` implementation alongside it — clone behaviour first, then
diff against the new Kotlin's compiler sources for the APIs that have
actually moved.

The drift catalogue in
`.local/ticket/done/task-028-api-drift-observation.md` (internal) documents
every API drift the project has encountered between 2.0.x and 2.1.x. Use it
as a checklist of likely failure points.

## 4. Verify locally

```bash
# Unit tests (kctfork against the version embedded in the test classpath)
./gradlew :compiler-plugin:test

# JVM end-to-end (#1 – #100)
./gradlew :integration-test:test-jvm:test

# KMP end-to-end (#101 – #105)
./gradlew :integration-test:test-kmp:jvmTest
```

If you also want to exercise the new version in the Gradle-plugin path:

```bash
./gradlew :integration-test:test-gradle-plugin:test
```

## 5. Extend CI

Add the new Kotlin version to the CI matrix
(`.github/workflows/*.yml`). The existing matrix iterates the same set of
JDKs against each declared Kotlin version, so usually a one-line addition
to the `kotlin:` axis is enough.

## 6. Update consumer-visible docs

- [README.MD](../README.MD) / [README.ja.md](../README.ja.md) — update the
  **Supported Kotlin versions** section.
- [docs/known-limitations.md](./known-limitations.md) — add or remove
  entries if the new Kotlin minor changes the parser / IR behaviour the
  current limitations rely on.

---

## Reference

- Compat layer architecture and runtime selection algorithm:
  [compiler-plugin/compat/README.md](../compiler-plugin/compat/README.md)
- Original inspiration: [Metro `compiler-compat`](https://github.com/ZacSweers/metro)
  (Apache 2.0). The CaptureCode layer is a focused subset; the
  `CompatContext` Metro-pattern adaptation is documented in
  [architecture.md](./architecture.md).
