# Known Limitations

The following constraints come from the **Kotlin 2.x K2 compiler itself**
(parser / IR / build environment) and the plugin deliberately does **not**
paper over them. Each limitation links to the integration-test case that
keeps the current behaviour locked in as a regression detector.

Confirmed and frozen on 2026-05-14, after Phase 1 – 3 of the implementation
plan.

---

## 1. `@Marker (expr)` requires explicit parentheses on the marker

- **Symptom**: Writing an expression annotation as `@CaptureExpr (1 + 2)`
  causes the K2 parser to greedily consume `(1 + 2)` as the **constructor
  arguments** of `CaptureExpr` instead of treating it as an annotated
  expression. The site source is then either wrong or unparseable.
- **Cause**: Kotlin 2.x K2 parser annotation-argument disambiguation. The
  plugin cannot intervene before the parser commits to that interpretation.
- **Workaround**: Always write the marker with an explicit empty argument
  list — `@CaptureExpr() (1 + 2)`. Marker constructors are required to have
  all-default arguments anyway (enforced by the Logic F checker).

## 2. `@Marker () ({ … })` strips the outer parentheses from lambda sites

- **Symptom**: A parenthesised lambda such as
  `@CaptureLambda() ({ println("clicked") })` captures the source as
  `{ println("clicked") }` — the outermost pair of parentheses is gone.
- **Cause**: The K2 parser attaches annotations directly to the inner
  `FirAnonymousFunctionExpression`. The surrounding `(...)` is outside the
  annotation's syntactic scope.
- **Workaround**: Prefer `@Marker() run { … }` (recommended in the README's
  capture-site overview). When you must use `@Marker () ({ … })`, accept
  that the site source will not include the outer parentheses.

## 3. KMP `expect` / `actual` — annotation on `expect` is lost

- **Symptom**: When `commonMain` has
  `@Marker internal expect fun foo()` and a target source set has
  `@Marker internal actual fun foo() { … }`, the plugin captures only the
  **actual** site. The design intent was to capture both.
- **Cause**: K2 IR's expect / actual matching drops the `expect` declaration
  from the IR module fragment whenever a matching `actual` exists. The
  annotation on the `expect` side never reaches the IR phase and is
  invisible to the IR collector.
- **Workaround**: None on the plugin side. The integration-test suite locks
  the observed behaviour in (case #103 expects 1 capture, not 2). If you
  need the source of the `expect` declaration, declare it as a normal
  function in `commonMain` instead.

## 4. `capturedSources<T>()` must be in the same compilation invocation as the markers

- **Symptom**: In a KMP project, putting the marker definition and use
  sites in `commonMain` while the `capturedSources<T>()` call lives in
  `jvmTest` (or any other test source set whose compilation is a separate
  Kotlin invocation) results in
  `IllegalStateException: CaptureCode compiler plugin is not applied`,
  because the in-memory registry is empty in the test compilation.
- **Cause**: The plugin's in-process registries
  (`CaptureCodeMarkerRegistry`, `CaptureCodeExpressionSiteRegistry`) only
  live for a single Kotlin invocation. `compileKotlinJvm` and
  `compileTestKotlinJvm` are separate invocations.
- **Workaround**: Keep markers, use sites and the `capturedSources<T>()`
  call in **the same compilation invocation** — i.e. the same source-set
  tree. The KMP samples and the `integration-test/test-kmp` module
  intentionally put everything in the test source sets.

## 5. Apple targets are opt-in

- **Symptom**: By default, `./gradlew :integration-test:test-kmp:assemble`
  does not build `iosX64` / `iosArm64` / `iosSimulatorArm64` / `macosX64` /
  `macosArm64` targets.
- **Cause**: Apple-native compilation requires Xcode, which is unavailable
  on Linux / Windows local-dev machines and the default Linux CI runner.
- **Workaround**: Pass `-PenableAppleTargets=true` to Gradle when building
  on a macOS host with Xcode installed.

---

For the full internal reasoning, ticket references and source-of-truth
discussion, see the unredacted catalogue in
`.local/compiler-plugin-design.md` §13 (internal — git-ignored).
