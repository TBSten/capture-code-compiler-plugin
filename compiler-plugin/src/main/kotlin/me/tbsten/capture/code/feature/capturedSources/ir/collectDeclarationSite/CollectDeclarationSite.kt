package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.findKDocExtendedStartOffset
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias

/**
 * Logic B-ir: declaration / file annotation / expression site の収集本体。
 *
 * task-120-B Phase 3a で concrete 化。 これまで各 `compat-kXXX/K{XXX}CapturedSourcesCollector.kt`
 * に重複していた declaration walk / file annotation 抽出 / expression site 抽出 / marker class
 * 除外 / KDoc 抽出 / per-marker config キャッシュを **K2.0 baseline の main module 1 箇所** に
 * 集約した版。
 *
 * ## 責務
 *
 * - [invoke] が moduleFragment 全体を走査して `List<CollectedSite>` を返す orchestrator
 * - IR 走査本体 (= visitor base class drift) は [CompatContext.walkIrFileDeclarations] に委譲
 * - file text loading (PSI / fileEntry drift) は [CompatContext.loadFileText] に委譲
 * - 各 site 種別の収集経路 (declaration / file annotation / expression) は 同 package 内の
 *   internal top-level helper に切り出し (1 ファイル 500 行制限のため)
 *
 * ## 旧構造との関係 (Phase 3a 時点)
 *
 * 既存の `K{XXX}CapturedSourcesCollector` は **並行存在** する。 Phase 5 で `transformIr` 経由の
 * wiring を main 経由に切り替えるまでは、 既存 collectors が runtime path として残り続ける。
 * Phase 6 で各 compat-kXXX の旧 collector を削除する。
 *
 * ## ファイル分割
 *
 * Phase 3a の collector ロジックは元 [K200CapturedSourcesCollector](https://...) で 685 行あった
 * ため、 1 ファイル 500 行制限に従って下記 3 ファイルに分割:
 *
 * - 本ファイル (`CollectDeclarationSite.kt`) — class 本体 (invoke + per-file dispatcher) +
 *   元から main にあった pure helpers
 * - [collectIfMarked] / [collectFileAnnotations] / [collectExpressionSites] —
 *   各 site 経路 (internal top-level fun)
 * - [extractDeclarationSource] / [extractFileSource] / [extractExpressionSource] /
 *   [stripMarkerClassDeclarations] — source 抽出 helpers
 *
 * ## なぜ class with invoke パターンか
 *
 * task-120 で main 側 logic を `public class XxxLogic { public operator fun invoke(...) }`
 * パターンに統一するため。 pure helper はそのまま public method として残し、 単独利用も可能。
 */
public class CollectDeclarationSite {

    /**
     * moduleFragment 全体を走査し、 marker annotation がついた declaration / file annotation /
     * expression annotation site をまとめて [CollectedSite] のリストとして返す。
     *
     * 各 IrFile ごとに以下 3 経路を順に実行する:
     * 1. declaration 走査 ([CompatContext.walkIrFileDeclarations] 経由) — class / function /
     *    property / typealias の 4 種の declaration をすべて訪問し、 marker annotation がついて
     *    いれば [CollectedSite] を 1 つ生成 (複数 marker 同時付与なら marker 数分)
     * 2. file annotation 走査 (`IrFile.annotations` の marker filter) — `@file:Marker` で
     *    file 全体に付与された marker から [CollectedSite] (kind = FILE) を生成
     * 3. expression site 走査 ([CaptureCodeExpressionSiteRegistry] 経由) — FIR phase で
     *    push された site のうち本 file にマッチするものから [CollectedSite] (kind = EXPRESSION)
     *    を生成
     *
     * marker registry が空 (= `@CaptureCode` メタ annotation class が module 内に 1 つも存在
     * しない) の場合でも、 expression site registry に push されていれば EXPRESSION 起源は
     * 処理対象になり得るが、 marker filter (`CaptureCodeMarkerRegistry.isMarker`) で弾かれる
     * ため最終的には空リストが返る。
     *
     * @param moduleFragment IR transform 対象の moduleFragment (= 各 IrFile の集合)
     * @param pluginContext 現状未使用 (Phase 4a 以降で IR 構築時の symbol table 解決に使う想定)。
     *   Phase 3a の signature 統一のため受け取るだけ
     * @param compat IR primitive (`walkIrFileDeclarations`, `loadFileText`) を委譲する SPI
     * @param config global Gradle DSL config (per-marker override 適用前の値)
     * @return moduleFragment 全体から収集した [CollectedSite] のリスト (発見順)
     */
    public operator fun invoke(
        moduleFragment: IrModuleFragment,
        @Suppress("UNUSED_PARAMETER") pluginContext: IrPluginContext,
        compat: CompatContext,
        config: CaptureCodePluginConfig,
    ): List<CollectedSite> {
        val results = mutableListOf<CollectedSite>()
        val effectiveConfigCache = mutableMapOf<String, CaptureCodePluginConfig>()
        for (file in moduleFragment.files) {
            collectInFile(file, compat, config, effectiveConfigCache, results)
        }
        return results
    }

    /**
     * 1 IrFile 分の収集を行う。 file text は遅延ロード (cache) して、 declaration / file
     * annotation / expression site の 3 経路に渡す。
     */
    private fun collectInFile(
        file: IrFile,
        compat: CompatContext,
        config: CaptureCodePluginConfig,
        effectiveConfigCache: MutableMap<String, CaptureCodePluginConfig>,
        sink: MutableList<CollectedSite>,
    ) {
        val cachedFileText: String? by lazy { compat.loadFileText(file) }
        val packageFqn = file.packageFqName.asString()
        val filePath = file.fileEntry.name
        val context = CollectFileContext(
            file = file,
            packageFqn = packageFqn,
            filePath = filePath,
            config = config,
            effectiveConfigCache = effectiveConfigCache,
            cachedFileText = { cachedFileText },
            site = this,
        )

        // 経路 1: file annotation (`@file:Marker`)
        collectFileAnnotations(context, sink)

        // 経路 2: declaration 走査 (class / function / property / typealias)
        compat.walkIrFileDeclarations(
            file = file,
            onClass = { collectIfMarked(it, classKindFor(it), context, sink) },
            onSimpleFunction = {
                // property accessor (getter / setter) は IrProperty 経由でキャプチャするので skip。
                if (it.correspondingPropertySymbol == null) {
                    collectIfMarked(it, CapturedSite.CaptureKind.FUNCTION, context, sink)
                }
            },
            onProperty = { collectIfMarked(it, CapturedSite.CaptureKind.PROPERTY, context, sink) },
            onTypeAlias = { collectIfMarked(it, CapturedSite.CaptureKind.TYPEALIAS, context, sink) },
        )

        // 経路 3: expression site (FIR session storage 由来)
        collectExpressionSites(context, sink)
    }

    /** [IrClass.kind] に基づく [CapturedSite.CaptureKind] mapping。 */
    private fun classKindFor(irClass: IrClass): CapturedSite.CaptureKind = when (irClass.kind) {
        ClassKind.OBJECT -> CapturedSite.CaptureKind.OBJECT
        // CLASS / INTERFACE / ANNOTATION_CLASS / ENUM_CLASS / ENUM_ENTRY は CLASS に集約
        else -> CapturedSite.CaptureKind.CLASS
    }

    // ---- pure helpers (元から main にあったもの。 単独利用も可能なため public 維持) ----

    /**
     * `startOffset` を行頭まで遡らせ、 さらに直前の行が **declaration modifier のみで構成された行**
     * または **annotation 行 (`@<Name>` で始まる行)** であれば、 さらに前の行までスキャンして
     * 拡張する。 KDoc コメントブロックや declaration 本体行に到達した時点で停止する。
     *
     * Kotlin 2.2.x 以降の IR では `IrDeclaration.startOffset` が宣言キーワード (`val` / `fun` /
     * `class` / `object` / `typealias`) の位置を指し、 modifier / annotation を含まない。
     * K200/K210 baseline では `@Marker` 行を含む位置を指していたため、 本メソッドは 2.2+ で
     * baseline と同等の startOffset を再構成する補正レイヤ。
     */
    public fun expandStartToCoverModifierAndAnnotationLines(fullText: String, startOffset: Int): Int {
        if (startOffset <= 0 || startOffset > fullText.length) return startOffset
        val lineStart = lineStartOffsetOf(fullText, startOffset)
        val prefix = fullText.substring(lineStart, startOffset)
        var current = if (prefix.isBlank() || prefixIsAllModifierTokens(prefix)) lineStart else startOffset
        if (current != lineStart) return current
        while (current > 0) {
            val prevLineEnd = current - 1
            if (prevLineEnd < 0) break
            if (fullText[prevLineEnd] != '\n') break
            val prevLineStart = lineStartOffsetOf(fullText, prevLineEnd)
            val prevLine = fullText.substring(prevLineStart, prevLineEnd)
            if (!isModifierOrAnnotationLine(prevLine)) break
            current = prevLineStart
        }
        return current
    }

    /**
     * 行頭 `lineStart` から `startOffset` までの prefix が、 空白とすべて modifier token
     * (例: `const`, `suspend`, `inline` …) のみで構成されているかを判定する。
     */
    private fun prefixIsAllModifierTokens(prefix: String): Boolean {
        val trimmed = prefix.trim()
        if (trimmed.isEmpty()) return true
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.all { it in DECLARATION_MODIFIERS }
    }

    /** `offset` を含む行の行頭 offset (= 直前の `\n` の次、 または 0) を返す。 */
    private fun lineStartOffsetOf(text: String, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    /**
     * 行が「modifier のみで構成された行」または「annotation で始まる行 (`@...`)」であるかを判定する。
     */
    private fun isModifierOrAnnotationLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return false
        if (trimmed.startsWith("//")) return false
        if (trimmed.startsWith("@")) return true
        val tokens = trimmed.split(Regex("\\s+"))
        return tokens.all { it in DECLARATION_MODIFIERS }
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、 先頭の **marker annotation 行** (改行まで) を
     * スキップした offset を返す。
     *
     * marker annotation **そのもの** (`@<simpleName>` + optional `(<args>)`) を token として識別し、
     * 末尾の空白 (改行を含む) を吸収する。 marker でない annotation (`@JvmInline` 等) はソースとして残す。
     *
     * @param markerSimpleNames marker FqN の simple name 集合 (= class 名のみ抜き出したもの)
     */
    public fun skipLeadingAnnotationLines(
        text: String,
        startOffset: Int,
        endOffset: Int,
        markerSimpleNames: Set<String>,
    ): Int {
        var cursor = startOffset
        while (cursor < endOffset) {
            val lineStart = cursor
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor >= endOffset || text[cursor] != '@') {
                return lineStart
            }
            val nameStart = cursor + 1
            var nameEnd = nameStart
            while (nameEnd < endOffset) {
                val ch = text[nameEnd]
                if (ch.isLetterOrDigit() || ch == '_') nameEnd++ else break
            }
            val simpleName = if (nameEnd > nameStart) text.substring(nameStart, nameEnd) else ""
            if (simpleName !in markerSimpleNames) {
                return lineStart
            }
            cursor = nameEnd
            if (cursor < endOffset && text[cursor] == '(') {
                var depth = 0
                while (cursor < endOffset) {
                    when (text[cursor]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) {
                                cursor++
                                break
                            }
                        }
                    }
                    cursor++
                }
                if (depth != 0) return lineStart
            }
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor < endOffset && text[cursor] == '\n') {
                cursor++
            }
        }
        return cursor
    }

    /**
     * declaration の `startOffset` 直前に位置する KDoc コメントブロック (`/** ... */`) を
     * raw text として抽出する。 KDoc が見つからない場合は空文字列を返す。
     */
    public fun extractKdocPrefix(fullText: String, startOffset: Int): String {
        val kdocStart = findKDocExtendedStartOffset(fullText, startOffset)
        if (kdocStart >= startOffset) return ""
        var lineStart = kdocStart
        while (lineStart > 0 && fullText[lineStart - 1] != '\n') {
            val ch = fullText[lineStart - 1]
            if (ch != ' ' && ch != '\t') break
            lineStart--
        }
        return fullText.substring(lineStart, startOffset).trimEnd()
    }

    /**
     * 両端を **対応する 1 ペアの括弧で完全に囲まれている** かつ **内部が `{` `}` で始まる lambda
     * 形式ではない** 場合に限り、 最外殻 `(` `)` を取り除く。
     *
     * 具体例:
     * - `"(1 + 2)"` → `"1 + 2"`
     * - `"({ println(\"x\") })"` → `"({ println(\"x\") })"` (parenthesis-lambda 形式なので保持)
     * - `"run { ... }"` → `"run { ... }"` (`(` で始まらないので無変更)
     */
    public fun stripSurroundingParens(text: String): String {
        if (text.length < 2) return text
        if (text.first() != '(' || text.last() != ')') return text
        val inner = text.substring(1, text.length - 1)
        val trimmedInner = inner.trimStart()
        if (trimmedInner.startsWith('{')) return text
        var depth = 0
        for ((index, ch) in text.withIndex()) {
            when (ch) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && index != text.lastIndex) return text
                }
            }
        }
        return if (depth == 0) text.substring(1, text.length - 1) else text
    }

    /**
     * registry に登録された site の filePath が IR file path に一致するかを判定する。
     *
     * 一致条件 (いずれか):
     * 1. site.filePath == irFilePath (= 絶対パス完全一致)
     * 2. site.filePath が irFilePath の末尾要素と一致 (= ファイル名のみで一致)
     * 3. irFilePath が site.filePath の末尾要素と一致 (= 逆方向)
     */
    public fun matchesFile(site: CaptureCodeExpressionSiteRegistry.Site, irFilePath: String): Boolean {
        val sitePath = site.filePath
        if (sitePath == irFilePath) return true
        val siteLeaf = sitePath.substringAfterLast('/').substringAfterLast('\\')
        val irLeaf = irFilePath.substringAfterLast('/').substringAfterLast('\\')
        if (siteLeaf == irLeaf && siteLeaf.isNotEmpty()) return true
        if (irFilePath.endsWith(sitePath) || sitePath.endsWith(irFilePath)) return true
        return false
    }

    public companion object {
        /**
         * Kotlin の declaration modifier 集合。 K200 baseline で startOffset が含んでいたが、
         * Kotlin 2.2+ では除外されるため、 これらを行レベルで吸い戻すために使う。
         */
        public val DECLARATION_MODIFIERS: Set<String> = setOf(
            "public", "private", "protected", "internal",
            "open", "final", "abstract", "sealed", "override",
            "data", "inner", "value", "enum", "annotation", "companion",
            "suspend", "inline", "noinline", "crossinline", "tailrec",
            "operator", "infix", "external",
            "const", "lateinit",
            "reified", "vararg",
            "expect", "actual",
        )
    }
}

/**
 * 1 IrFile 分の収集中に [CollectDeclarationSite] の各 internal helper に共有される immutable
 * context。 引数列を肥大化させず、 helper 間で共通の参照を渡すために使う。
 *
 * - [file] 走査対象の IrFile
 * - [packageFqn] 当該 file の package FqN (file 毎に 1 回だけ取得)
 * - [filePath] 当該 file の path (`IrFile.fileEntry.name`)
 * - [config] global Gradle DSL config
 * - [effectiveConfigCache] marker FqN → effective config の per-module キャッシュ
 * - [cachedFileText] 当該 file の text を遅延ロードして返す lambda (失敗時 `null`)
 * - [site] pure helper を呼び出すための [CollectDeclarationSite] back-reference
 *   (`stripSurroundingParens` などを再利用するため)
 */
internal class CollectFileContext(
    val file: IrFile,
    val packageFqn: String,
    val filePath: String,
    val config: CaptureCodePluginConfig,
    val effectiveConfigCache: MutableMap<String, CaptureCodePluginConfig>,
    val cachedFileText: () -> String?,
    val site: CollectDeclarationSite,
)
