package me.tbsten.capture.code.gradle

import java.io.File
import java.security.MessageDigest
import org.gradle.api.Project

/**
 * Compute a stable hash representing the "Capture Code marker world" of a
 * Gradle project — the set of marker annotation classes (i.e. classes meta-
 * annotated with `@CaptureCode`) plus every source file that uses any of
 * those markers (`@MarkerName ...`).
 *
 * ## なぜ必要か
 *
 * Kotlin の Gradle incremental compile (IC) は ABI-level dependency tracking
 * を行うため、 「`@Snippet` annotated 宣言の追加 / 削除 / 編集」 は caller
 * 側 (`capturedSources<Snippet>()`) のソースファイル変更を伴わないと
 * dependency に拾われず、 caller の再 compile がスキップされ、 stale な
 * `capturedSources` 結果が返り続ける (impl-plan §4 リスク R5)。
 *
 * 本 hasher は marker class とその use sites の文字列内容を一括 hash し、
 * Gradle plugin が各 `KotlinCompile` task の `inputs.property` に attach する
 * ことで、 marker 集合の何らかの変化があれば確実に caller を含む module 全体の
 * task を non-up-to-date にして強制的に rebuild させる。
 *
 * ## 検出範囲
 *
 * - **marker class**: `@CaptureCode` annotation を class declaration に直接
 *   付与しているファイル (= marker そのものの宣言)。
 * - **marker use sites**: 上記で集めた marker class 名 (simple name) を
 *   `@<MarkerName>` 形式で含むファイル (= 実際に capture される宣言)。
 *
 * 両方の変化に反応する必要があるのは、 marker class 自体の属性 (`@CaptureCode`
 * の引数) が変わったり、 use site が増減 / 編集された時いずれも caller の
 * `capturedSources<T>()` 結果が変わるため。
 *
 * 検出は **正規表現を使わない素朴な文字列 contains** で十分。 false positive
 * は build を「実際より広く invalidate する」 だけで安全側 (build correctness
 * は壊さない)。 false negative は R5 を再現させるので避ける必要があるが、
 * `@CaptureCode` / `@<MarkerName>` という固定 token が source に出現する性質
 * 上、 素朴 contains で取りこぼしは起きない (Kotlin の annotation syntax は
 * 必ず `@` で始まる)。
 *
 * ## 性能
 *
 * 全 `.kt` を一度 read するため、 大規模 module では数十 MB 単位の I/O が
 * 走る。 ただし Gradle Provider 経由で task 起動時のみ評価されるため、
 * configuration phase のオーバーヘッドはゼロ。 必要なら content cache を
 * 追加できるが、 本実装ではまず単純さを優先。
 */
internal object MarkerSetHasher {
    private const val CAPTURE_CODE_TOKEN = "@CaptureCode"
    private const val CAPTURE_CODE_FQN_TOKEN = "@me.tbsten.capture.code.CaptureCode"

    /**
     * Project + 全 subproject の `src/` 配下を walk して marker world の
     * SHA-256 hex digest を返す。 marker が一つも見つからなければ "0" を返す
     * (= IC を従来通り効かせる中立値)。
     */
    fun hashFor(project: Project): String {
        // 1. project tree の全 .kt ファイルを収集。
        val sourceFiles = collectKotlinSourceFiles(project)
        if (sourceFiles.isEmpty()) return EMPTY_HASH

        // 2. marker class を含むファイルを抽出し、 marker 名 (= 含む `@CaptureCode`
        //    の直後にある class 名) を集める。
        val markerNames = mutableSetOf<String>()
        val markerClassFiles = mutableListOf<Pair<File, String>>()
        for (file in sourceFiles) {
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            if (content.contains(CAPTURE_CODE_TOKEN) || content.contains(CAPTURE_CODE_FQN_TOKEN)) {
                markerClassFiles += file to content
                markerNames += extractMarkerSimpleNames(content)
            }
        }
        if (markerNames.isEmpty()) return EMPTY_HASH

        // 3. marker class 名で annotated されている use site を抽出。
        //    `@<MarkerName>` という素朴 contains で OK (Kotlin annotation syntax 上)。
        val useSiteFiles = mutableListOf<Pair<File, String>>()
        val markerUsageTokens = markerNames.map { "@$it" }
        for (file in sourceFiles) {
            // marker class ファイル自身も use site 候補に含めず別経路で hash 済み。
            if (markerClassFiles.any { it.first == file }) continue
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            if (markerUsageTokens.any { token -> content.contains(token) }) {
                useSiteFiles += file to content
            }
        }

        // 4. SHA-256 で集約。 path 順 sort で hash の安定性を確保。
        val digest = MessageDigest.getInstance("SHA-256")
        (markerClassFiles + useSiteFiles)
            .sortedBy { it.first.absolutePath }
            .forEach { (file, content) ->
                digest.update(file.absolutePath.toByteArray(Charsets.UTF_8))
                digest.update(0)
                digest.update(content.toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * project + 全 subproject の `src/` 配下から `.kt` ファイルを列挙。
     *
     * Gradle の `Project.fileTree("src")` を使うと configuration-cache や
     * task input 追跡と相性が悪いケースがあるため、 ここでは `java.io.File`
     * で直接 walk する (本関数は task action 内 = execution phase でのみ
     * 呼ばれるので問題ない)。
     */
    private fun collectKotlinSourceFiles(project: Project): List<File> {
        val result = mutableListOf<File>()
        for (sub in project.allprojects) {
            val srcRoot = sub.projectDir.resolve("src")
            if (!srcRoot.isDirectory) continue
            srcRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { result += it }
        }
        return result
    }

    /**
     * marker class のシンプル名を素朴に抽出。 `@CaptureCode ... annotation class Foo`
     * のような形を期待し、 marker 名 (`Foo`) を返す。 厳密 parser ではなく、
     * `annotation class <Name>` token の直後の identifier を取り出す方式。
     *
     * marker class が複数 (例: 1 ファイルに 2 つの marker) 宣言されていても
     * 検出できる。 false positive ("annotation class" が文字列 literal 内に
     * 出てくる場合等) は IC invalidation が広がるだけで害はない。
     */
    private fun extractMarkerSimpleNames(content: String): List<String> {
        val names = mutableListOf<String>()
        var searchStart = 0
        while (true) {
            val idx = content.indexOf("annotation class", searchStart)
            if (idx < 0) break
            val nameStart = idx + "annotation class".length
            // skip whitespace
            var i = nameStart
            while (i < content.length && content[i].isWhitespace()) i++
            val identStart = i
            while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '_')) i++
            if (i > identStart) {
                names += content.substring(identStart, i)
            }
            searchStart = i
        }
        return names
    }

    /**
     * marker が見つからなかった時の中立 hash。 普通に空文字でも良いが、
     * 「marker が一つも無い build」 と 「hash 計算をスキップした build」 を
     * Gradle 上で区別したい時のために固定 sentinel 文字列にしておく。
     */
    private const val EMPTY_HASH = "no-capture-code-markers"
}
