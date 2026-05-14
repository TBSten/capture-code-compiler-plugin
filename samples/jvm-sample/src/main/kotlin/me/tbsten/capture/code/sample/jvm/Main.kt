package me.tbsten.capture.code.sample.jvm

import me.tbsten.capture.code.capturedSources

// ============================================================================
// JVM sample のエントリーポイント。
//
// 各 marker に対して `capturedSources<T>()` を呼び、 plugin が module 内の
// 使用箇所を集めて埋めてくれた結果を stdout に出力する。 cookbook 各サンプルの
// 動作を一望できる demo として機能する。
//
// 実行方法:
//   ./gradlew :samples:jvm-sample:run
// ============================================================================

fun main() {
    printSection("単純な marker (Snippet)") {
        capturedSources<Snippet>().forEach { snippet ->
            println("- source: ${snippet.source.value}")
        }
    }

    printSection("全 filler (DetailedSnippet)") {
        capturedSources<DetailedSnippet>().forEach { d ->
            println("- kind=${d.kind.value} pkg=${d.location.packageName}")
            println("  line=${d.location.startLine}-${d.location.endLine}")
            println("  source: ${oneLine(d.source.value)}")
        }
    }

    printSection("user-defined params (Route)") {
        capturedSources<Route>().forEach { r ->
            println("- ${r.method} ${r.path}")
            println("  impl: ${oneLine(r.source.value)}")
        }
    }

    printSection("全宣言ターゲット (TypeDoc)") {
        capturedSources<TypeDoc>().forEach { t ->
            println("- kind=${t.kind.value}")
            println("  source: ${oneLine(t.source.value)}")
        }
    }

    printSection("file annotation (FileTopic)") {
        capturedSources<FileTopic>().forEach { f ->
            println("- topic=${f.topic}")
            // ファイル全体が source に入っているので、 行数だけ示す。
            val lines = f.source.value.lines()
            println("  file source lines: ${lines.size}")
            println("  first non-blank: ${lines.firstOrNull { it.isNotBlank() } ?: "<empty>"}")
        }
    }
}

private fun printSection(title: String, body: () -> Unit) {
    println()
    println("=== $title ===")
    body()
}

/** 複数行を 1 行にまとめる (デモ表示用)。 */
private fun oneLine(s: String): String =
    s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ⏎ ")
