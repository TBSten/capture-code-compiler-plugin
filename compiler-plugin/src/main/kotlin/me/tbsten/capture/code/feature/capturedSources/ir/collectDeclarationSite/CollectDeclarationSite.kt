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
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate] = main 側 IR extension) は
 * 以下を保証する責務がある。 違反した場合の挙動は **silent skip + verbose-log** で plugin
 * 開発者の debug を補助するに留め、 `require(...)` での fail-fast は導入していない (= source
 * 抽出失敗で全 site を drop するより、 file 単位 / declaration 単位での silent skip + debug log
 * のほうが user 体験として safer)。
 *
 * - `moduleFragment: IrModuleFragment` は IR phase で plugin context に与えられる引数 (signature 上保証)。
 *   `moduleFragment.files` は iterate 可能 (IR API 仕様)。
 * - 各 `IrFile` の `fileEntry.name` が non-null (IR API 仕様)。
 * - `compat: CompatContext` は同 module の `CompatContextImpl` actual 実装で、 `walkIrFileDeclarations`
 *   (declaration walk)、 `loadFileText` (PSI 経由 file text load) の SPI が正しく dispatch される。
 * - `pluginContext: IrPluginContext` は IR phase 用 (現状は未使用、 phase 4a 以降の symbol 解決
 *   で利用予定)。 `@Suppress("UNUSED_PARAMETER")` で型保持のみ。
 * - `config: CaptureCodePluginConfig` は `CaptureCodePluginConfigHolder` が publish した
 *   global config。 typical root cause: holder の `compute()` が呼ばれる前に invoke された (=
 *   compiler-plugin の phase 順序 bug)。 silent fallback では DEFAULT config が使われる。
 * - `compat.loadFileText(file)` が `null` を返す場合、 当該 file の全 site を silent skip し、
 *   `CaptureCodeMessageCollectorHolder.reportLogging` で LOGGING level の breadcrumb を残す
 *   (= `--info` 等 verbose build でのみ visible)。 typical root cause: KMP の klib で source
 *   が見つからない / synthetic file が混入。
 * - [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   は FIR phase 完了後の状態。 marker 未登録の compilation でも空 list が返るのは正常。
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
        // BOM-stripped 化。 source file 先頭に UTF-8 BOM (U+FEFF) が含まれている場合、
        // PSI 経由 / filesystem 経由で取得した raw text には BOM が残るが、 IR の startOffset /
        // endOffset は BOM を含まない座標系で計算されているため、 raw text のままだと 1 char ずれて
        // 全ての declaration 起源 source 抽出が off-by-one になる (= marker line 漏れ + 末尾 char
        // 欠落)。 ここで先頭 BOM を一律 strip することで IR offset と整合する text を提供する。
        val cachedFileText: String? by lazy {
            compat.loadFileText(file)?.let { text ->
                if (text.isNotEmpty() && text[0] == '﻿') text.substring(1) else text
            }
        }
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
     *
     * **注意**: 同一行に annotation と declaration keyword (`val` / `var` / `fun` / `class` / `object`
     * / `interface` / `typealias` / `enum`) の両方がある行 (例: `@Marker val a = 1`) は declaration
     * 行とみなし、 annotation 行扱いしない (= 遡り停止)。 これは [expandStartToCoverModifierAndAnnotationLines]
     * の while ループが「前 declaration 行」 まで遡って source に leak させないための gating 条件。
     *
     * caveat: annotation argument 内の string literal に keyword 文字列が含まれるケース
     * (例: `@Marker("val foo")`) は false negative となり、 当該行は annotation 行と判定されない
     * = 遡り停止する。 broken syntax 経路だが、 string literal 内に "val" を含む annotation も
     * 同じ理由で遡り停止する側に倒れる (= 安全側)。
     */
    private fun isModifierOrAnnotationLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return false
        if (trimmed.startsWith("//")) return false
        val tokens = trimmed.split(Regex("\\s+"))
        // 同一行に annotation + declaration keyword (val/var/fun/...) があれば declaration 行扱い
        // (遡り停止)。 これにより `@Marker val a = 1` 行を annotation 行と誤判定して前 declaration
        // line まで遡る bug (= prev-property-leak) を防ぐ。
        if (tokens.any { it in DECLARATION_KEYWORDS }) return false
        if (trimmed.startsWith("@")) return true
        return tokens.all { it in DECLARATION_MODIFIERS }
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、 先頭の **marker annotation 行** (改行まで) を
     * スキップした offset を返す。
     *
     * marker annotation **そのもの** (`@<simpleName>` + optional `(<args>)`) を token として識別し、
     * 末尾の空白 (改行を含む) を吸収する。 marker でない annotation (`@JvmInline` 等) はソースとして残す。
     *
     * **BUG-A 注意 (`task-129`)**: 本関数は「先頭が非 marker annotation だったらそこで打ち切り」
     * という古い方針のため、 「`@Suppress("unused") → @Marker → fun ...`」 の順で並んでいる場合に
     * 中間の marker 行を drop できず source に leak する。 後方互換のため signature は維持しつつ、
     * 新規呼び出しは [skipLeadingMarkerAnnotations] (= [SkipMarkerResult] を返す版) を使うこと。
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
            // bug-005: `@example.Snippet` のような FQN 記法に対応するため、 `.` 区切りの
            // qualified name として読み進め、 末尾 segment を simple name として照合する。
            val nameScan = readQualifiedAnnotationName(text, nameStart, endOffset)
            val simpleName = nameScan.simpleName
            if (simpleName !in markerSimpleNames) {
                return lineStart
            }
            cursor = nameScan.nameEnd
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
     * `startOffset` 〜 `endOffset` の範囲の先頭から連続する **annotation 行 + blank 行** を走査し、
     * 「source の開始 offset」 と 「途中に存在する **marker annotation token range** リスト」 を返す。
     *
     * BUG-A (`task-129`) の修正版。 [skipLeadingAnnotationLines] が「先頭が非 marker annotation
     * だったら打ち切り」 だったため `@Suppress → @Marker → fun ...` の中間 marker を drop
     * できなかった。 本関数は 1 pass 走査で:
     *
     * - **marker annotation** (`@<simpleName>` + optional `(...)`) を見つけたら、 その token range
     *   (line head から token 終端 + 行末改行までを含む) を [SkipMarkerResult.markerRanges] に記録
     * - **非 marker annotation** (`@JvmInline` 等) と **blank 行** はそのまま保持 (source 開始点候補)
     * - 「`@` でも空行でもない (= modifier / `fun` / `val` / `class` / `object` / `typealias` 等)」
     *   行に到達したら走査終了
     *
     * source 開始 offset は「先頭の non-marker / non-blank 行の頭」 (= 最初に保持される行の lineStart)
     * を返す。 全行が marker または blank の場合は最終 cursor を返す。
     *
     * extractDeclarationSource は [SkipMarkerResult.sourceStart] で substring した上で、
     * [SkipMarkerResult.markerRanges] の range を **降順** で drop すれば leak を防げる。
     *
     * @param markerSimpleNames marker FqN の simple name 集合 (= class 名のみ抜き出したもの)。
     *   import alias で marker を書ける file では alias も含める ([markerSimpleNames] が
     *   [markerImportAliases] を union して構築する)
     */
    public fun skipLeadingMarkerAnnotations(
        text: String,
        startOffset: Int,
        endOffset: Int,
        markerSimpleNames: Set<String>,
    ): SkipMarkerResult {
        val markerRanges = mutableListOf<IntRange>()
        var sourceStart = startOffset
        var sourceStartFixed = false
        var cursor = startOffset
        while (cursor < endOffset) {
            val lineStart = cursor
            // 行頭 whitespace を skip
            var lineContentStart = cursor
            while (lineContentStart < endOffset &&
                (text[lineContentStart] == ' ' || text[lineContentStart] == '\t')
            ) {
                lineContentStart++
            }
            if (lineContentStart >= endOffset) {
                // 行末まで whitespace のみ → blank 行扱い (= 走査は終了)
                if (!sourceStartFixed) {
                    sourceStart = lineStart
                    sourceStartFixed = true
                }
                break
            }
            if (text[lineContentStart] != '@') {
                // 非 annotation 行に到達 → 走査終了 (= declaration 本体 or modifier 行)
                if (!sourceStartFixed) {
                    sourceStart = lineStart
                    sourceStartFixed = true
                }
                break
            }
            // 行頭が '@'。 simpleName を読む。 bug-005: `@example.Snippet` のような FQN 記法に
            // 対応するため、 `.` 区切りの qualified name として読み進め、 末尾 segment を
            // simple name として照合する。
            val nameStart = lineContentStart + 1
            val nameScan = readQualifiedAnnotationName(text, nameStart, endOffset)
            val simpleName = nameScan.simpleName
            // annotation argument `(...)` を skip (depth-balanced)
            var afterArgs = nameScan.nameEnd
            if (afterArgs < endOffset && text[afterArgs] == '(') {
                var depth = 0
                while (afterArgs < endOffset) {
                    when (text[afterArgs]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) {
                                afterArgs++
                                break
                            }
                        }
                    }
                    afterArgs++
                }
                if (depth != 0) {
                    // unbalanced `(...)` → 安全側で走査終了
                    if (!sourceStartFixed) {
                        sourceStart = lineStart
                        sourceStartFixed = true
                    }
                    break
                }
            }
            // annotation 末尾 → 行末まで whitespace を吸収
            var afterTrailing = afterArgs
            while (afterTrailing < endOffset &&
                (text[afterTrailing] == ' ' || text[afterTrailing] == '\t')
            ) {
                afterTrailing++
            }
            val lineEndAfterNewline = if (afterTrailing < endOffset && text[afterTrailing] == '\n') {
                afterTrailing + 1
            } else {
                afterTrailing
            }

            if (simpleName in markerSimpleNames) {
                // marker 行 → range として記録、 source 開始点は更新しない
                if (sourceStartFixed) {
                    // 既に非 marker 行を確定済 → token のみ drop (改行も含めて drop しないと
                    // source 内の改行レイアウトが崩れるので、 line 全体を drop range にする)
                    markerRanges += lineStart until lineEndAfterNewline
                }
                // sourceStart が未確定の場合は marker 行は完全 skip (= drop range に入れない、
                // ただし sourceStart は marker 行直後の cursor に進める)
                cursor = lineEndAfterNewline
            } else {
                // 非 marker annotation 行 → source として保持。 sourceStart を確定する
                if (!sourceStartFixed) {
                    sourceStart = lineStart
                    sourceStartFixed = true
                }
                cursor = lineEndAfterNewline
            }
        }
        if (!sourceStartFixed) sourceStart = cursor
        return SkipMarkerResult(sourceStart = sourceStart, markerRanges = markerRanges)
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
     * 1. path separator 正規化後の完全一致 (= 通常経路。 FIR / IR とも絶対パスを返す)
     * 2. 一方が他方の **path segment 境界に沿った suffix** (= 片方が絶対パス、 もう片方が
     *    source root 相対パスで返ってきた場合の吸収)。 suffix 側は 1 段以上のディレクトリを
     *    含む必要がある。
     *
     * ## BUG-I (task-149): bare file name 一致は誤マッチの温床
     *
     * 以前は上記に加えて
     *
     * - 「file 名 (leaf) だけの一致」 (`a/Basic.kt` vs `b/Basic.kt` → **一致扱い**)
     * - 「segment 境界を見ない `String.endsWith`」 (`Basic.kt` vs `MyBasic.kt` → **一致扱い**)
     *
     * を許していた。 expression site は `(filePath, startOffset, endOffset)` の 3 つ組で
     * source を切り出すため、 誤マッチした file では **同じ offset が別 file の text に適用され、
     * 無関係な位置の文字列が capture される** (offset が range 外なら silent skip、 range 内なら
     * garbage を掴む)。 feature ごとに `Basic.kt` を置くような実プロジェクトでは同名 file が
     * 何個も並ぶため、 1 個の `@Marker run { ... }` から複数の garbage site が生まれていた。
     *
     * bare file name しか持たない site は **完全一致のみ** を許す (= 誤って別 file の text を
     * 掴むくらいなら capture されない方が安全) 方針に倒している。
     */
    public fun matchesFile(site: CaptureCodeExpressionSiteRegistry.Site, irFilePath: String): Boolean {
        val sitePath = site.filePath.normalizePathSeparators()
        val irPath = irFilePath.normalizePathSeparators()
        if (sitePath.isEmpty() || irPath.isEmpty()) return false
        if (sitePath == irPath) return true
        return sitePath.isPathSuffixOf(irPath) || irPath.isPathSuffixOf(sitePath)
    }

    /**
     * Windows 形式の `\\` 区切りを `/` に寄せて比較可能な形にする。
     */
    private fun String.normalizePathSeparators(): String =
        if (indexOf('\\') < 0) this else replace('\\', '/')

    /**
     * この文字列が [full] の **path segment 境界に沿った** 真の suffix かどうか。
     *
     * - `"featureA/Basic.kt".isPathSuffixOf("/tmp/src/featureA/Basic.kt")` → `true`
     * - `"featureB/Basic.kt".isPathSuffixOf("/tmp/src/featureA/Basic.kt")` → `false`
     * - `"Basic.kt".isPathSuffixOf("/tmp/src/featureA/Basic.kt")` → `false`
     *   (bare file name は同名 file を巻き込むため受け付けない。 上位 [matchesFile] の KDoc 参照)
     * - `"asic.kt".isPathSuffixOf("/tmp/src/Basic.kt")` → `false` (segment 途中で切れている)
     */
    private fun String.isPathSuffixOf(full: String): Boolean {
        if (indexOf('/') < 0) return false
        if (length >= full.length) return false
        if (!full.endsWith(this)) return false
        return full[full.length - length - 1] == '/'
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

        /**
         * Kotlin の declaration keyword 集合 (= `val` / `var` / `fun` / `class` / `object` /
         * `interface` / `typealias` / `enum`)。 [isModifierOrAnnotationLine] で「同一行に
         * annotation + declaration keyword がある行」 を declaration 行と判定して遡り停止する
         * ために使う。 prev-property-leak (= `@Marker val a = 1\n@Marker val b = 2` のような
         * 1 行 1 property 形式で前の declaration が source に leak する) を防ぐ。
         *
         * NOTE: `enum` は `DECLARATION_MODIFIERS` にも含まれるが、 `enum class Foo` のように
         * declaration keyword としても使われるため、 keyword 側に重複して列挙している。
         */
        public val DECLARATION_KEYWORDS: Set<String> = setOf(
            "val", "var", "fun", "class", "object", "interface", "typealias", "enum",
        )
    }
}

/**
 * [CollectDeclarationSite.skipLeadingMarkerAnnotations] の戻り値。
 *
 * - [sourceStart] : declaration source として抽出を始める offset (= 最初の非 marker / 非 blank 行の頭、
 *   または全行 marker / blank だった場合は走査終了 cursor)
 * - [markerRanges] : `[sourceStart, endOffset)` の範囲内に存在する **marker annotation 行 range** のリスト。
 *   各 range は line head から trailing newline まで (= `start until endExclusive`) で表現される。
 *   raw substring 抽出後、 これらの range を **降順** で drop すれば marker literal の leak を防げる。
 *
 * BUG-A (`task-129`) の修正で導入された型。
 */
public data class SkipMarkerResult(
    val sourceStart: Int,
    val markerRanges: List<IntRange>,
)

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
