# CaptureCode

自分で定義した annotation でコードをマークするだけで、**コンパイル時にソース文字列がキャプチャ**されて runtime にデータとして取り出せる Kotlin compiler plugin。reflection なし、runtime コストゼロ。

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

`@CaptureCode` を付けた annotation を 1 つ定義する。それでマークした宣言・式・ファイルが対象になる。`capturedSources<T>()` で集めると、各サイトの **ソース文字列がそのまま** 入った annotation インスタンスのリストが返る。

---

## ユーザ定義のパラメータも自由に持てる

filler 型 (`Source` / `SourceLocation` / `CaptureKind`) と、自分で決めたパラメータ (id / label / priority など) は共存できる。filler は型で識別されるので、ユーザ定義の値は use site で指定した値がそのまま保持される。

```kotlin
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class HttpRoute(
    val method: Method,                                 // ← 自分の値
    val path: String,                                   // ← 自分の値
    val source: Source = Source(),                      // ← plugin が埋める
    val location: SourceLocation = SourceLocation(),    // ← plugin が埋める
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

## library 提供の filler 型

marker annotation のパラメータ型として宣言すれば plugin が値を埋める。**宣言しなければ何もしない (opt-in)**。

| 型 | 役割 |
|---|---|
| `Source(val value: String)` | キャプチャされたソース文字列 |
| `SourceLocation(packageName, filePath, startLine, endLine)` | キャプチャサイトの位置情報 |
| `CaptureKind(val value: Kind)` | `EXPRESSION` / `PROPERTY` / `CLASS` / `OBJECT` / `FUNCTION` / `TYPEALIAS` / `FILE` |

---

## キャプチャできる場所

```kotlin
// 宣言
@Marker val prop = 1
@Marker class MyClass
@Marker fun myFun() = 2
@Marker object MyObj
@Marker typealias MyAlias = Int

// ファイル全体
@file:Marker

// 式
val r = @Marker (1 + 2 + 3)
val block = @Marker run { ... }
```

---

## 使い道

- **ドキュメント生成** — コードサンプルを文章とコード本体に二重に書かない
- **CLI コマンド / REST ルートのカタログ**
- **マイグレーションスクリプト・DB スキーマ宣言の収集**
- **フィーチャーフラグ・設定キーの一覧**
- **ベンチマーク・テストフィクスチャの登録**
- **DI binding カタログ** (Anvil / Metro 風)
- **チュートリアルや BDD scenario のステップ収集**

---

## 制約

- Kotlin **2.x (K2)** のみ
- marker annotation は **`internal` または `private`** 必須 — 単一モジュール内に閉じた設計
- **KMP 対応** — JVM / JS / Native / WASM。commonMain と各 platform source set で marker 定義・use site・取得が可能

---

設計・内部実装は [DESIGN.md](DESIGN.md) を参照。テストカタログは [test-cases.md](test-cases.md) に 100 ケース。