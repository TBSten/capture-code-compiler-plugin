# CaptureCode

A Kotlin compiler plugin that **captures the source string at compile time** simply by marking code with an annotation you define yourself, then lets you read it back as data at runtime. No reflection, zero runtime cost.

```kotlin
import io.github.tbsten.capturecode.*

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

## Constraints

- Kotlin **2.x (K2)** only
- Marker annotations must be **`internal` or `private`** — by design, capture stays confined to a single module
- **KMP support** — JVM / JS / Native / WASM. Marker definitions, use sites, and retrieval all work in `commonMain` and in each platform source set

---

For the design and internal implementation, see the internal design notes. The test catalog lives in [test-cases.md](test-cases.md), with 100 cases.
