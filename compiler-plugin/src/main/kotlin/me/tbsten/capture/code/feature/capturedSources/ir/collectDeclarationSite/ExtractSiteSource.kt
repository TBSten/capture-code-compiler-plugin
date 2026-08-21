package me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.feature.capturedSources.ir.extractSourceText.ExtractSourceText
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.NormalizeSource
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toDeclarationNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toExpressionNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toFileNormalizeOptions
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * declaration 1 件分の source 抽出 + 正規化。
 *
 * 処理ステップ:
 * 1. file text を取得 (取得不可なら `null`)
 * 2. declaration の `startOffset..endOffset` 範囲 + offset validity 確認
 * 3. `includeKdoc = true` なら直前 KDoc を抽出 ([CollectDeclarationSite.extractKdocPrefix])
 * 4. 先頭の marker / 非 marker annotation 行を 1 pass 走査 ([CollectDeclarationSite.skipLeadingMarkerAnnotations])
 *    で「source 開始 offset」 と 「中間 marker range のリスト」 を取得
 * 5. raw substring 抽出 ([ExtractSourceText]) → marker range を **降順** で drop
 * 6. KDoc prefix と body を結合
 * 7. [NormalizeSource] で dedent / blank trim 等を適用 ([toDeclarationNormalizeOptions])
 *
 * BUG-A (`task-129`) 修正前は step 4 で「先頭の marker 行のみ skip」 する 1 段階処理だったため、
 * 「`@Suppress("unused") → @Marker → fun ...`」 の順で並んでいる場合に中間 marker 行が drop
 * されず source に leak していた。 修正後は **走査 (source 範囲確定) と drop (marker range) を分離**
 * することで、 marker と 非 marker annotation がどの順序で並んでいても正しく扱える。
 *
 * 失敗条件 (`null` 返却):
 * - file text が読めない
 * - declaration の offset が UNDEFINED (-1) または不正
 * - offset が file text の範囲外
 *
 * ## Preconditions
 *
 * Caller (= [collectIfMarked]) は以下を保証する責務がある。 違反時は当該 marker 1 件のみ silent
 * skip し、 LOGGING level の breadcrumb を残す設計のため、 `require(...)` での fail-fast は導入
 * していない (= 1 file での source 抽出失敗で全 site を crash させるより safer)。
 *
 * - `declaration: IrDeclarationBase` は IR resolution 完了済 (= caller の declaration walk から
 *   届く)。 marker annotation はすでに `markerAnnotations()` filter を pass。
 * - `effective: CaptureCodePluginConfig` は global config と per-marker override を合成した
 *   immutable snapshot (= [CollectFileContext.effectiveConfigFor] キャッシュ経由)。
 * - `cachedFileText: String?` は file text の遅延 PSI access の結果。 `null` の場合は当該
 *   declaration 起源 site を skip し LOGGING で可視化 (= verbose build でのみ visible)。
 *   typical root cause: KMP klib で source が見つからない / synthetic file 混入。
 * - `site: CollectDeclarationSite` は pure helper (= `expandStartToCoverModifierAndAnnotationLines`
 *   / `extractKdocPrefix` / `skipLeadingMarkerAnnotations`) を呼ぶための back-reference。
 *   state を持たないので thread-safe。
 */
internal fun extractDeclarationSource(
    declaration: IrDeclarationBase,
    effective: CaptureCodePluginConfig,
    cachedFileText: String?,
    site: CollectDeclarationSite,
): String? {
    val fullText = cachedFileText ?: run {
        // task-137: file text が読めない場合は declaration 起源 site を skip するだけで
        // user 通常 build には影響しないが、 plugin 開発者の debug 用に LOGGING level で
        // breadcrumb を残す (= `--info` 等の verbose build でのみ表示)。
        CaptureCodeMessageCollectorHolder.reportLogging(
            "[CaptureCode] Failed to load file text for declaration " +
                "(offset ${declaration.startOffset}..${declaration.endOffset}); " +
                "skipping declaration site.",
        )
        return null
    }
    val rawStartOffset = declaration.startOffset
    val endOffset = declaration.endOffset
    if (rawStartOffset < 0 || endOffset < 0 || rawStartOffset >= endOffset) return null
    if (endOffset > fullText.length) return null
    // K2.3+ では IR declaration の startOffset が **modifier (`internal` 等) と annotation
    // 行の後 (= `fun` / `val` トークン直前)** を指すように変わった (drift D-IR-34)。
    // 一方 K2.0-K2.2 baseline では startOffset が annotation / modifier 行の **先頭** から
    // 始まる。 plugin としては「@Marker 行は skip、 modifier 含む宣言本体は残す」 挙動が
    // 期待される。 `expandStartToCoverModifierAndAnnotationLines` で startOffset を modifier
    // / annotation 行の先頭まで戻すことで、 全 baseline で同一の挙動 (modifier 含む宣言が
    // 残り、 `@Marker` 行は skipLeadingMarkerAnnotations で drop される) を保証する。
    val startOffset = site.expandStartToCoverModifierAndAnnotationLines(fullText, rawStartOffset)
    // `includeKdoc = true` (デフォルト) の場合、 declaration の startOffset の
    // 直前にある KDoc を別途抽出する。 KDoc は `@Marker` 行より手前にあるため、
    // 単純に startOffset を前方拡張すると `@Marker` 行が skip されない問題がある
    // (走査関数は連続する annotation / blank 行のみ走査するため、 KDoc 行で中断する)。
    // そこで KDoc 抽出と body 抽出を **分離** し、 後で連結する戦略を採る。
    val kdocPrefix = if (effective.includeKdoc) site.extractKdocPrefix(fullText, startOffset) else ""
    val skipResult = site.skipLeadingMarkerAnnotations(
        fullText, startOffset, endOffset, markerSimpleNames(),
    )
    val rawBody = ExtractSourceText()(fullText, skipResult.sourceStart, endOffset) ?: return null
    // sourceStart 以降に位置する marker annotation 行を drop。
    // rawBody は `fullText.substring(sourceStart, endOffset)` なので、 marker range の offset を
    // sourceStart 相対に変換した上で descending 順で削除する (削除順により後続の offset が崩れないため)。
    val rawBodyWithoutMarkerLeak = if (skipResult.markerRanges.isEmpty()) {
        rawBody
    } else {
        val builder = StringBuilder(rawBody)
        skipResult.markerRanges
            // marker range は `lineStart until lineEndAfterNewline` (= half-open) で作られているので、
            // IntRange.first / last は それぞれ inclusive 端。 sourceStart 相対に変換しつつ、
            // StringBuilder.delete(start, end) (= half-open `[start, end)`) に渡すために
            // `last + 1` を end として使う。
            .map { range ->
                val start = range.first - skipResult.sourceStart
                val endExclusive = range.last + 1 - skipResult.sourceStart
                start to endExclusive
            }
            .filter { (start, endExclusive) ->
                start in 0..builder.length && endExclusive in 0..builder.length && start < endExclusive
            }
            .sortedByDescending { it.first }
            .forEach { (start, endExclusive) -> builder.delete(start, endExclusive) }
        builder.toString()
    }
    val rawText = if (kdocPrefix.isNotEmpty()) {
        kdocPrefix + "\n" + rawBodyWithoutMarkerLeak
    } else {
        rawBodyWithoutMarkerLeak
    }
    return NormalizeSource()(rawText, effective.toDeclarationNormalizeOptions())
}

/**
 * file 全体テキスト → marker class declaration 除外 → file normalize の順で source を抽出する。
 *
 * marker class 自身は `@CaptureCode` メタ annotation 付きの annotation class 定義であり、
 * file 起源 capture の **対象** ではない。 marker class の declaration を file source から
 * drop することで、 ユーザ視点で「キャプチャされるべきコード」だけが残るようにする。
 *
 * ## Preconditions
 *
 * Caller (= [collectFileAnnotations]) は以下を保証する責務がある。 違反時は当該 file の marker
 * 1 件のみ silent skip + LOGGING breadcrumb で、 `require(...)` での fail-fast は導入していない。
 *
 * - `file: IrFile` は IR resolution 完了済の file。 `file.declarations` は marker class
 *   declaration の resolution 完了済 (= `IrClass.fqNameWhenAvailable` が解決可能)。
 * - `effective: CaptureCodePluginConfig` は当該 marker fqn 用の effective config (= file 起源
 *   marker の override も合成済)。
 * - `cachedFileText: String?` は file text の遅延 PSI access の結果。 `null` の場合は当該 file
 *   起源 marker を silent skip。 typical root cause: KMP klib / synthetic file 混入。
 */
internal fun extractFileSource(
    file: IrFile,
    effective: CaptureCodePluginConfig,
    cachedFileText: String?,
): String? {
    val fullText = cachedFileText ?: run {
        // task-137: 通常は file 単位の `cachedFileText()` が一度成功してから本関数が呼ばれる
        // ため null になる経路は限定的だが、 IR file 経路 (= file-level marker) の skip を
        // plugin 開発者向けに LOGGING で可視化する。
        CaptureCodeMessageCollectorHolder.reportLogging(
            "[CaptureCode] Failed to load file text for file '${file.fileEntry.name}'; " +
                "skipping file-level capture site.",
        )
        return null
    }
    val withoutMarkers = stripMarkerClassDeclarations(file, fullText)
    return NormalizeSource()(withoutMarkers, effective.toFileNormalizeOptions())
}

/**
 * expression annotation 起源の raw 抽出 + 正規化。
 *
 * FIR session が push する `(startOffset, endOffset)` は対象 expression の source range。
 * 抽出後に [CollectDeclarationSite.stripSurroundingParens] で 両端 `(` `)` を strip し、
 * [toExpressionNormalizeOptions] で expression normalize を適用。
 *
 * ## Preconditions
 *
 * Caller (= [collectExpressionSites]) は以下を保証する責務がある。 違反時は当該 expression
 * site 1 件のみ silent skip し、 `require(...)` での fail-fast は導入していない (= invalid
 * range の expression は ExtractSourceText で null fallback で安全に skip される)。
 *
 * - `fullText: String` は当該 file の text (= cachedFileText から取得済の non-null snapshot)。
 * - `startOffset < endOffset && both >= 0` は FIR phase の [CollectExpressionSite] で validate
 *   済 (= UNDEFINED_OFFSET -1 や逆転 offset は push されない)。 ただし `ExtractSourceText` が
 *   改めて範囲 check を行うため、 ここでは require せず安全。
 * - `effective: CaptureCodePluginConfig` は当該 expression marker の effective config。
 * - `site: CollectDeclarationSite` は pure helper (= `stripSurroundingParens`) を呼ぶための
 *   back-reference。 state なし thread-safe。
 */
internal fun extractExpressionSource(
    fullText: String,
    startOffset: Int,
    endOffset: Int,
    effective: CaptureCodePluginConfig,
    site: CollectDeclarationSite,
    unwrapBlockBody: Boolean = false,
): String? {
    val raw = ExtractSourceText()(fullText, startOffset, endOffset) ?: return null
    if (unwrapBlockBody) {
        // task-150: `runWithCaptureCode(Marker::class) { ... }` 起源。 FIR は **呼び出し式全体** の
        // range を push してくる (= lambda の AST 形 drift を避けるため) ので、 file text を持っている
        // ここで最外殻の `{` `}` を落として body だけにする。 body の先頭は必ず改行または空白なので、
        // 1 行目インデント復元 ([reattachOwnLeadingIndent]) は不要で、 素直に dedent すれば揃う。
        val body = stripEnclosingBlock(raw) ?: return null
        return NormalizeSource()(body, effective.toExpressionNormalizeOptions())
    }
    val stripped = site.stripSurroundingParens(raw)
    // task-149 (BUG-J): expression の startOffset は **行頭ではなく式そのもの** を指すため、
    // 抽出した text の 1 行目だけ見かけ上のインデントが 0 になる。 そのまま dedent すると
    // 「非空行の最小インデント = 0」 と判定されて 2 行目以降が元 file の絶対インデントの
    // まま取り残される (`run {` / `        println(..)` / `    }` のような崩れた形になる)。
    // 式が行頭から始まる (= 行頭から startOffset までが whitespace のみ) 場合に限り、
    // その whitespace を 1 行目に戻してから normalize することで、 dedent が
    // 「この式だけを column 0 に持ってきた形」 に正しく揃うようにする。
    val withOwnIndent = reattachOwnLeadingIndent(fullText, startOffset, stripped)
    return NormalizeSource()(withOwnIndent, effective.toExpressionNormalizeOptions())
}

/**
 * 最外殻の `{` `}` を落として block の body だけを返す。
 *
 * `runWithCaptureCode(Marker::class) { ... }` の **呼び出し式全体** から lambda body を取り出すために
 * 使う。 呼び出しの signature は `(marker: KClass<out Annotation>, block: () -> R)` で、 marker 引数は
 * `Foo::class` 形式しか取り得ない (= Kotlin の型引数は `<>`、 class literal に `{` は現れない) ため、
 * **最初の `{` が lambda の開き括弧** であることが構文的に保証される。 同様に閉じ括弧は最後の `}`。
 *
 * - `"f(M::class) {\n  a()\n}"` → `"\n  a()\n"`
 * - `"f(M::class) {}"` → `""` (空 block)
 * - `"f(M::class)"` → `null` (`{` が無い)
 * - `"}{"` → `null` (閉じが開きより前)
 *
 * 返り値は **正規化前** の raw body。 dedent / blank trim は呼び出し側の [NormalizeSource] が行う。
 */
internal fun stripEnclosingBlock(text: String): String? {
    val open = text.indexOf('{')
    if (open < 0) return null
    val close = text.lastIndexOf('}')
    if (close <= open) return null
    return text.substring(open + 1, close)
}

/**
 * [text] (= [startOffset] から抽出した expression の raw source) の先頭に、
 * **その式が元 file で持っていた行頭インデント** を復元して返す。
 *
 * 復元するのは以下をすべて満たす場合のみ:
 * - [text] が複数行 (= 1 行なら dedent の最小値計算に影響しないので no-op)
 * - 行頭から [startOffset] までが space / tab のみ (= 式が行頭から始まっている)
 *
 * `val x = @Marker() run { ... }` のように **行の途中** から始まる式は、 行頭までの文字列が
 * 式と無関係 (`val x = @Marker() `) なのでインデント扱いできない。 この場合は [text] を
 * そのまま返し、 従来どおりの挙動 (= 1 行目 0 インデント基準) を維持する。
 */
private fun reattachOwnLeadingIndent(fullText: String, startOffset: Int, text: String): String {
    if (!text.contains('\n')) return text
    if (startOffset <= 0 || startOffset > fullText.length) return text
    val lineStart = fullText.lastIndexOf('\n', startOffset - 1) + 1
    if (lineStart >= startOffset) return text
    val indent = fullText.substring(lineStart, startOffset)
    if (indent.any { it != ' ' && it != '\t' }) return text
    return indent + text
}

/**
 * raw file text から、 本 [file] 内に定義された **marker class declaration** (= `@CaptureCode`
 * メタ付き annotation class) の `startOffset..endOffset` 範囲を drop した文字列を返す。
 *
 * marker class が複数ある場合は **降順** (`endOffset` の大きい順) に drop することで、
 * 早い range の drop が後の range の offset を invalid にしない (raw text の offset と
 * marker class の startOffset は同じ座標系)。
 *
 * marker class declaration の startOffset は Kotlin 2.0.0 の IR では先頭 `@Marker` 行を
 * 含む可能性があるが、 本関数は **既知の marker registry に登録された** annotation class
 * のみを対象とするため、 drop 範囲が広めでも問題ない (誤って削れる場合もない)。
 */
internal fun stripMarkerClassDeclarations(file: IrFile, text: String): String {
    val markerRanges = file.declarations
        .filterIsInstance<IrClass>()
        .filter { irClass ->
            val fqn = irClass.fqNameWhenAvailable?.asString() ?: return@filter false
            CaptureCodeMarkerRegistry.isMarker(fqn)
        }
        .mapNotNull { irClass ->
            val startOffset = irClass.startOffset
            val endOffset = irClass.endOffset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) null
            else if (endOffset > text.length) null
            else startOffset..endOffset
        }
        .sortedByDescending { it.last }
    if (markerRanges.isEmpty()) return text
    val builder = StringBuilder(text)
    for (range in markerRanges) {
        builder.delete(range.first, range.last)
    }
    return builder.toString()
}

/**
 * marker registry から「marker FqN の simpleName 集合 (= class 名のみ抜き出したもの)」を返す。
 *
 * 各 collect 経路で `skipLeadingAnnotationLines` に渡す `markerSimpleNames` 引数のため、
 * 都度計算 (= 1 declaration 1 回呼び出し) で問題ない (marker 数は通常 数件 〜 十数件)。
 */
internal fun markerSimpleNames(): Set<String> =
    CaptureCodeMarkerRegistry.markerFqns.mapTo(mutableSetOf()) { it.substringAfterLast('.') }
