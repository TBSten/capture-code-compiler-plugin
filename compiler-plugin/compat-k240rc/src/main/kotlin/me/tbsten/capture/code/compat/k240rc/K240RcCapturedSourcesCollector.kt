package me.tbsten.capture.code.compat.k240rc

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.capturedSources.CapturedSite
import me.tbsten.capture.code.feature.markerDefinition.effectiveFor
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.NormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.findKDocExtendedStartOffset
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.normalize
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toDeclarationNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toExpressionNormalizeOptions
import me.tbsten.capture.code.feature.capturedSources.ir.normalize.toFileNormalizeOptions
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * collector が収集する 1 件分のキャプチャ情報。
 *
 * 公開 API である [CapturedSite] に加えて、 marker annotation 自身の [IrConstructorCall] を
 * 保持する。 これにより rewriter は call site でユーザが渡した argument
 * (例: `@Foo(id = X, label = "y")` の `X` / `"y"`) を IR 上の式として直接取り出して
 * 新しい marker instance に詰め直すことができる。
 *
 * `:compat` の `CapturedSite` 自体に IR types を晒すと API surface が広くなるため、
 * compat-k2000 内部だけで使う internal な data class として切り出している (SSOT は CapturedSite、
 * markerCall はあくまで collector → rewriter のローカル受け渡し情報)。
 */
internal data class K240RcCapturedSiteData(
    val site: CapturedSite,
    /**
     * `@Marker(...)` 自身に対応する IR コンストラクタ呼び出し。 declaration / file 起源では
     * 必ず IR の `IrConstructorCall` が存在するため non-null だが、
     * **EXPRESSION 起源** では IR phase に annotation が残らないため **`null`** になる。
     *
     * rewriter は markerCall == null の場合、ユーザ定義 parameter について
     * [me.tbsten.capture.code.compat.k240rc.userargs.UserArgIrBuilder.buildOrDefault] の
     * default 値 fallback 経路と、FIR session から渡された [userArgs] (= primitive 値) で
     * IR 式を構築する経路を併用する。
     *
     * declaration / file 起源は引き続き `markerCall` を通じて `getValueArgument(i)` で
     * call site の式を deepCopy する経路を使う。
     */
    val markerCall: IrConstructorCall?,
    /**
     * EXPRESSION 起源で、FIR session storage から取り出した「filler 以外」のパラメータ値。
     *
     * declaration / file 起源では `markerCall` から直接 IR 式が取れるので、本フィールドは **空 Map**。
     * EXPRESSION 起源では FIR phase で抜き出した primitive / enum FqN を name → value で持ち、
     * rewriter が IR const へ変換して marker constructor へ詰める。
     *
     * 現在は filler のみが入った marker (ケース #7, #67 等) を主にサポート。
     * primitive 引数を持つ marker のキャプチャは将来拡張する。
     */
    val expressionUserArgs: Map<String, Any?> = emptyMap(),
    /**
     * 当該 site に対して計算済の effective config (= global Gradle DSL config に
     * marker 単位の override [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions]
     * を適用した結果)。 marker に override が無い (引数なしの `@CaptureCode`) 場合は
     * global config と同一インスタンスになる ([CaptureCodePluginConfig.effectiveFor] の fast-path)。
     */
    val effectiveConfig: CaptureCodePluginConfig,
)

/**
 * `@CaptureCode` メタ付き marker annotation (= [CaptureCodeMarkerRegistry] に登録された FqN) を
 * 持つ宣言を IR 走査で収集する visitor。
 *
 * Logic A (動的検出) により hardcoded marker FqN list は撤廃済みで、 本 collector は
 * [CaptureCodeMarkerRegistry] (FIR phase で動的検出された marker FqN の集合) を参照する。
 *
 * ## サポート対象 (Logic B-ir)
 *
 * **宣言レベル 5 種類** をサポートする:
 *
 * | 種別 | IR ノード | visitor | [CapturedSite.CaptureKind] |
 * |------|----------|---------|----------------------------|
 * | property | [IrProperty] | [visitProperty] | [CapturedSite.CaptureKind.PROPERTY] |
 * | class / interface / annotation class | [IrClass] (`kind != OBJECT`) | [visitClass] | [CapturedSite.CaptureKind.CLASS] |
 * | object / companion | [IrClass] (`kind == OBJECT`) | [visitClass] | [CapturedSite.CaptureKind.OBJECT] |
 * | function | [IrSimpleFunction] (property accessor 除く) | [visitSimpleFunction] | [CapturedSite.CaptureKind.FUNCTION] |
 * | typealias | [IrTypeAlias] | [visitTypeAlias] | [CapturedSite.CaptureKind.TYPEALIAS] |
 *
 * **ファイル全体** (`@file:Marker`) も収集対象。 `IrElementVisitorVoid` の再帰経路では
 * `IrFile.annotations` (= file-level annotation) を訪問しないため、 本 collector の
 * [collectFileAnnotations] を [K240RcIrInjector] パス 1 から **declaration 走査と並行で** 呼び出す
 * (visitor では拾えないので別 entry point を用意する)。
 *
 * design §4 F2 「ファイル」/ §5 Logic B 参照。
 *
 * ## 走査と再帰
 *
 * `IrElementVisitorVoid` は default の [visitElement] で `acceptChildrenVoid(this)` を呼ぶことで
 * 再帰するが、`visitClass` / `visitSimpleFunction` などを override すると default の再帰チェーンを
 * 自分で繋ぐ必要がある (deepwiki/JetBrains/kotlin confirmed)。
 * 本 collector は **各 visit メソッドの末尾で `acceptChildrenVoid(this)` を呼ぶ** ことで、
 * nested declaration (例: class 内の property / member function) も漏れなく走査する。
 *
 * ## ソーステキスト取得 (Logic C 最小実装)
 *
 * `IrFileEntry.getSourceRangeInfo` は行列情報のみで生テキストを保持しないため、生テキストは
 * 次の優先順位で取得する:
 *
 * 1. [PsiIrFileEntry] が利用可能ならその PSI file text から substring
 * 2. PSI が無い (LightTree / `NaiveSourceBasedFileEntryImpl` など) 場合は
 *    [IrFile.fileEntry] の `name` (絶対パス) から `File.readText()` する
 *
 * 同一 [IrFile] への複数 declaration 処理時のテキスト重複読み込みは [cachedFileText] で
 * file 単位キャッシュする (design §5.C "file 単位でテキストをキャッシュ" の最小版)。
 *
 * ## アノテーション行の除外 (design §7.2)
 *
 * Kotlin 2.0.0 の IR では各 declaration の `startOffset` が先頭 `@Marker` 行を含む位置を指す
 * (実機確認済み)。 [skipLeadingAnnotationLines] で行頭 `@` 行を改行までスキップする。
 * 複数 annotation や `@JvmInline` 等の Kotlin 標準 annotation 行も **すべて** スキップ
 * (空行があれば残す) する Phase 1 ポリシー。 厳密な「marker annotation のみスキップ」モードは
 * DSL option (`includeAnnotationLines`) 配線後に検討する。
 */
internal class K240RcCapturedSourcesCollector(
    private val currentFile: IrFile,
    private val config: CaptureCodePluginConfig,
) : IrVisitorVoid() {

    /**
     * 収集された capture site データ (CapturedSite + marker IrConstructorCall) のリスト。
     *
     * 公開 API `CapturedSite` に加えて marker annotation の `IrConstructorCall` も
     * 保持する。 後段の rewriter がユーザ定義パラメータの値を IR 上の式として
     * 取り出すために使う。
     *
     * 公開モデル ([CapturedSite]) のみを参照したい場合は [capturedSites] (compatibility view)。
     */
    val capturedSiteData: MutableList<K240RcCapturedSiteData> = mutableListOf()

    /**
     * 既存 API 互換のため、[capturedSiteData] から [CapturedSite] のみを抜き出したリスト。
     *
     * 既存テストや 旧コード経路が `capturedSites` を期待していた場合に備えて残しているが、
     * 新規呼び出し側は [capturedSiteData] を直接使うこと。
     */
    val capturedSites: List<CapturedSite>
        get() = capturedSiteData.map { it.site }

    /** 同一 [IrFile] に対する複数の declaration を処理する際に file テキストをキャッシュする。 */
    private val cachedFileText: String? by lazy { SourceTextExtractor.loadFileText(currentFile) }

    /**
     * marker FqN ごとの effective config キャッシュ。
     *
     * 同一 marker が file 内に複数 site (例: 4 つの property に同じ marker) ある場合に毎回
     * `effectiveFor` で新インスタンスを作らないよう、 1 marker 1 回だけ計算する。
     */
    private val effectiveConfigCache: MutableMap<String, CaptureCodePluginConfig> = mutableMapOf()

    /**
     * marker FqN に対する effective [CaptureCodePluginConfig] を返す。
     *
     * Logic A で per-marker option override (`@CaptureCode(includeKdoc = Override.Yes, ...)`) を
     * 読み取り済みの [CaptureCodeMarkerRegistry] を参照する。 marker に override が無い場合は
     * `config` (global Gradle DSL config) と同一インスタンスになる ([effectiveFor] の fast-path)。
     */
    private fun effectiveConfigFor(markerFqn: String): CaptureCodePluginConfig =
        effectiveConfigCache.getOrPut(markerFqn) {
            config.effectiveFor(CaptureCodeMarkerRegistry.markerOptionsFor(markerFqn))
        }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitProperty(declaration: IrProperty) {
        collectIfMarked(declaration, CapturedSite.CaptureKind.PROPERTY)
        // property の getter / setter は visitSimpleFunction 経由で走るが、accessor は
        // correspondingPropertySymbol が non-null なのでそこで skip される (二重 capture 回避)。
        super.visitProperty(declaration)
    }

    override fun visitClass(declaration: IrClass) {
        val kind = when (declaration.kind) {
            ClassKind.OBJECT -> CapturedSite.CaptureKind.OBJECT
            // CLASS / INTERFACE / ANNOTATION_CLASS / ENUM_CLASS / ENUM_ENTRY は CLASS に集約
            else -> CapturedSite.CaptureKind.CLASS
        }
        collectIfMarked(declaration, kind)
        super.visitClass(declaration)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        // property accessor (getter / setter) は IrProperty 経由でキャプチャするので skip。
        if (declaration.correspondingPropertySymbol == null) {
            collectIfMarked(declaration, CapturedSite.CaptureKind.FUNCTION)
        }
        super.visitSimpleFunction(declaration)
    }

    override fun visitTypeAlias(declaration: IrTypeAlias) {
        collectIfMarked(declaration, CapturedSite.CaptureKind.TYPEALIAS)
        super.visitTypeAlias(declaration)
    }

    /**
     * `@file:Marker` で file 全体に付いた marker annotation を走査し、
     * marker ごとに 1 件ずつ [CapturedSite] (`kind = FILE`) を生成する。
     *
     * `IrElementVisitorVoid` の再帰経路では `IrFile.annotations` を訪問しないため、本 method を
     * [K240RcIrInjector] パス 1 から **declaration 走査とは独立に** 呼ぶ。declaration 走査と
     * file 走査を分離することで、双方の収集経路が pollute されないことを保証する。
     *
     * ## 収集仕様
     *
     * - source: file 全体テキストを [SourceTextExtractor.loadFileText] で取得し、
     *   [fileNormalizeOptions] (= `stripPackageAndImport = !includeImports`) で正規化する
     * - filePath: `IrFile.fileEntry.name` (絶対パス。 Phase 2 はこのまま)
     * - packageFqn: `IrFile.packageFqName.asString()`
     * - startLine: **1** (file 起源は常にファイル先頭から)
     * - endLine: `IrFileEntry.getLineNumber(maxOffset) + 1` (`IrFileEntry.getLineNumber` は
     *   0-based なので +1 で 1-based に揃える)。`IrFileEntry.lineCount` のような便利プロパティは
     *   Kotlin 2.0.0 の `IrFileEntry` interface にはまだ存在しない (`maxOffset` / `getLineNumber`
     *   のみ stable)。
     *
     * design §4 F2 「ファイル」/ §5 Logic B (FIR: `FirFileAnnotationsContainer`、ただし
     * SOURCE retention でも IR phase に annotation が残ることを deepwiki で確認済) / §5 Logic D
     * step 5 (file 起源で package / import 除外) 参照。
     */
    fun collectFileAnnotations() {
        val fileAnnotations = currentFile.annotations.markerAnnotations()
        if (fileAnnotations.isEmpty()) return

        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // file の終端行は `fileEntry.maxOffset` から計算する。
        // IrFileEntry.getLineNumber は 0-based なので +1 で 1-based に揃える (filler の design 値域)。
        val endLine = currentFile.fileEntry.getLineNumber(currentFile.fileEntry.maxOffset) + 1
        for ((markerFqn, markerCall) in fileAnnotations) {
            val effective = effectiveConfigFor(markerFqn)
            val source = extractFileSource(effective) ?: continue
            capturedSiteData += K240RcCapturedSiteData(
                site = CapturedSite(
                    markerFqn = markerFqn,
                    source = source,
                    kind = CapturedSite.CaptureKind.FILE,
                    packageFqn = packageFqn,
                    filePath = filePath,
                    startLine = 1,
                    endLine = endLine,
                ),
                markerCall = markerCall,
                effectiveConfig = effective,
            )
        }
    }

    /**
     * FIR session storage ([CaptureCodeExpressionSiteRegistry]) に push された
     * 式 annotation site のうち、 本 [currentFile] にマッチするものを `CapturedSite(kind = EXPRESSION)`
     * に変換して [capturedSiteData] に追加する。
     *
     * design §5 Logic B-fir の **IR phase 側読み出し**:
     * 1. registry にある全 markerFqn / filePath の組み合わせを走査
     * 2. file path が `currentFile.fileEntry.name` と一致 (または fixture 名で一致) する site のみ採用
     * 3. site の `startOffset..endOffset` から `cachedFileText.substring` で raw text を抽出
     * 4. 周辺の `(` `)` を strip (= `KtParenthesizedExpression` 剥がし相当)
     * 5. [expressionNormalizeOptions] で dedent / trim blank
     * 6. [CapturedSite] (kind = EXPRESSION) として登録
     *
     * markerCall は EXPRESSION 起源では存在しないため `null`。 rewriter は default 値経路で
     * marker instance を組み立てる。 ユーザ定義 parameter (primitive 等) は FIR から
     * push された `userArgs` を経由する。
     *
     * file path の照合は **絶対パス完全一致** を試み、 ダメなら **末尾セグメント完全一致**
     * (`endsWith` 比較) に fallback する。 kctfork テスト fixture では FIR 側が PSI の name
     * (= ファイル名のみ) を push する可能性があり、 IR 側の `fileEntry.name` は temp dir の絶対パス
     * になりやすいため、 末尾一致で同一性を判定する。
     */
    fun collectExpressionSites() {
        if (CaptureCodeExpressionSiteRegistry.allSites.isEmpty()) return
        val fileText = cachedFileText ?: return
        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // FIR checker は marker 判定をせずに **すべての expression annotation** を push してくる
        // (FIR checker phase の ordering 問題回避のため)。 IR phase 側で
        // [CaptureCodeMarkerRegistry.isMarker] による filter を行う。
        val matchingSites = CaptureCodeExpressionSiteRegistry.allSites
            .asSequence()
            .filter { CaptureCodeMarkerRegistry.isMarker(it.markerFqn) }
            .filter { it.matchesFile(filePath) }
            .sortedBy { it.startOffset }
            .toList()

        for (site in matchingSites) {
            val effective = effectiveConfigFor(site.markerFqn)
            val source = extractExpressionSource(fileText, site.startOffset, site.endOffset, effective) ?: continue
            val startLine = currentFile.fileEntry.getLineNumber(site.startOffset) + 1
            val endLine = currentFile.fileEntry.getLineNumber(site.endOffset) + 1
            capturedSiteData += K240RcCapturedSiteData(
                site = CapturedSite(
                    markerFqn = site.markerFqn,
                    source = source,
                    kind = CapturedSite.CaptureKind.EXPRESSION,
                    packageFqn = packageFqn,
                    filePath = filePath,
                    startLine = startLine,
                    endLine = endLine,
                ),
                markerCall = null,
                expressionUserArgs = site.userArgs,
                effectiveConfig = effective,
            )
        }
    }

    /**
     * 式 annotation 起源の raw 抽出 + 正規化。
     *
     * FIR session が push する `(startOffset, endOffset)` は **`@Marker (expr)` のうちの「式部分」**
     * (annotation 自体ではなく対象 expression の source range)。
     * ただし観察と production の挙動には僅かに差異がある可能性があるため、
     * 抽出後に **両端の `(` `)` をペアでひと組ずつ strip** する補正を行う。これにより
     * `(1 + 2 + 3)` で push されても `1 + 2 + 3` に揃う。
     *
     * `@Marker run { ... }` (ケース #29) や `@Marker ({ ... })` (ケース #30) では range が
     * lambda / parenthesized expression 全体を覆うため、 strip は **両端が対応する場合のみ** 一度。
     * `({ x })` のような特殊形は strip しない (= `({ x })` のまま保つ)。 これは spike (f) で
     * design §3.4 / §7.8 「`run { }` 推奨」の方針と整合する。
     */
    private fun extractExpressionSource(
        fullText: String,
        startOffset: Int,
        endOffset: Int,
        effective: CaptureCodePluginConfig,
    ): String? {
        val raw = SourceTextExtractor.substringOrNull(fullText, startOffset, endOffset) ?: return null
        val stripped = stripSurroundingParens(raw)
        return normalize(stripped, effective.toExpressionNormalizeOptions())
    }

    /**
     * 両端を **対応する 1 ペアの括弧で完全に囲まれている** かつ **内部が `{` `}` で始まり/終わる lambda
     * 形式ではない** 場合に限り、最外殻 `(` `)` を取り除く。
     *
     * spec ケース #30 (`@Marker ({ ... })`) は parenthesis-lambda 全体を保つ仕様のため、
     * 内部が `{` で始まる場合は strip しない。
     *
     * 具体例:
     * - `"(1 + 2)"` → `"1 + 2"`
     * - `"(compute(\"a\" + \"b\") + compute(\"c\"))"` → `"compute(\"a\" + \"b\") + compute(\"c\")"`
     * - `"({ println(\"x\") })"` → `"({ println(\"x\") })"` (← parenthesis-lambda 形式なので保持)
     * - `"run { ... }"` → `"run { ... }"` (← `(` で始まらないので無変更)
     */
    private fun stripSurroundingParens(text: String): String {
        if (text.length < 2) return text
        if (text.first() != '(' || text.last() != ')') return text
        // parenthesis-lambda 形式 (`({...})`) は spec ケース #30 で全体保持の仕様
        val inner = text.substring(1, text.length - 1)
        val trimmedInner = inner.trimStart()
        if (trimmedInner.startsWith('{')) return text
        // 最外殻の `(` と `)` がペアになっていることを balanced check で確認
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
     * registry に登録された site の filePath が本 IrFile に一致するかを判定する。
     *
     * 一致条件 (いずれか):
     * 1. site.filePath == currentFile.fileEntry.name (= 絶対パス完全一致)
     * 2. site.filePath が currentFile.fileEntry.name の末尾要素と一致 (= ファイル名のみで一致)
     * 3. currentFile.fileEntry.name が site.filePath の末尾要素と一致 (= 逆方向)
     *
     * Kotlin compile-testing (kctfork) では FIR 側で `psi.containingFile?.name` だけが取れ、
     * IR 側の `fileEntry.name` は temp dir の絶対パスになりうるため、両方向の末尾一致を許容する。
     */
    private fun CaptureCodeExpressionSiteRegistry.Site.matchesFile(irFilePath: String): Boolean {
        if (filePath == irFilePath) return true
        val sitePath = filePath
        val sliding = sitePath.substringAfterLast('/').substringAfterLast('\\')
        val irLeaf = irFilePath.substringAfterLast('/').substringAfterLast('\\')
        if (sliding == irLeaf && sliding.isNotEmpty()) return true
        // 部分パス末尾一致 (例えば "src/main/.../FileA.kt" vs "/tmp/.../FileA.kt")
        if (irFilePath.endsWith(sitePath) || sitePath.endsWith(irFilePath)) return true
        return false
    }

    /**
     * file 全体テキストを取り出し、[fileNormalizeOptions] (file 起源用) で正規化する。
     *
     * declaration 起源と違い、annotation 行スキップ (`skipLeadingAnnotationLines`) は呼ばない。
     * 代わりに [NormalizeOptions.stripPackageAndImport] が `package` 行と `import` 行を除外する。
     * 先頭 `@file:Marker` 行も config の `includeAnnotationLines = false` なら
     * `stripLeadingAnnotationLines = true` 経由で normalize 内 で除外される。
     *
     * ## marker class 自身の declaration 除外
     *
     * file annotation は **その file 内のキャプチャ対象** を集める用途なので、`@CaptureCode`
     * メタ付き annotation class (= marker 自身) の declaration は capture 結果から除外する。
     * marker class 自身は capture のメタ情報のための定義であり、ユーザがその "ソース" を
     * 受け取ることに実用上の意味はない (design §3.1 / §3.2 の文脈から見ても、marker は
     * site **に付ける** ものであって site **そのもの** ではない)。
     *
     * 実装は [stripMarkerClassDeclarations] で、`IrFile.declarations` のうち
     * [CaptureCodeMarkerRegistry] に登録された FqN を持つ [IrClass] の `startOffset..endOffset`
     * 範囲を raw text から drop する形を採る。
     */
    private fun extractFileSource(effective: CaptureCodePluginConfig): String? {
        val fullText = cachedFileText ?: return null
        val withoutMarkers = stripMarkerClassDeclarations(fullText)
        return normalize(withoutMarkers, effective.toFileNormalizeOptions())
    }

    /**
     * raw file text から、本 file 内に定義された **marker class declaration** (= `@CaptureCode`
     * メタ付き annotation class) の `startOffset..endOffset` 範囲を drop した文字列を返す。
     *
     * marker class が複数ある場合は **降順** (`endOffset` の大きい順) に drop することで、
     * 早い range の drop が後の range の offset を invalid にしない (raw text の offset と
     * marker class の startOffset は同じ座標系)。
     *
     * marker class declaration の startOffset は Kotlin 2.0.0 の IR では先頭 `@Marker` 行を
     * 含む可能性があるが、本 method は **既知の marker registry に登録された** annotation class
     * のみを対象とするため、drop 範囲が広めでも問題ない (誤って削れる場合もない)。
     */
    private fun stripMarkerClassDeclarations(text: String): String {
        val markerRanges = currentFile.declarations
            .filterIsInstance<IrClass>()
            .filter { irClass ->
                val fqn = irClass.classFqName() ?: return@filter false
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

    /** `IrClass.kotlinFqName` の安定版。`internal` annotation class でも FqN を取得できる。 */
    private fun IrClass.classFqName(): String? = fqNameWhenAvailable?.asString()

    /**
     * 指定された宣言が marker annotation を持つ場合、ソースを抽出して [capturedSites] に追加する。
     *
     * **複数 marker 同時付与** (`@Foo @Bar fun x()`) に対応するため、annotation 列を **すべて**
     * 走査して [CaptureCodeMarkerRegistry] に登録されている FqN ごとに 1 件ずつ [CapturedSite] を
     * 生成する (design §7.7 「重複 marker」: エラーにせず両方の `capturedSources<T>()` に出る)。
     *
     * この method は **location 情報** (`packageFqn` / `filePath` / `startLine` /
     * `endLine`) も合わせて埋める。 後段 ([K240RcCapturedSourcesRewriter]) で
     * `SourceLocation` filler に注入される。
     */
    private fun collectIfMarked(
        declaration: IrDeclarationBase,
        kind: CapturedSite.CaptureKind,
    ) {
        // marker annotation の `IrConstructorCall` も合わせて取り出す。
        // 1 宣言に複数 marker が付いている場合 (`@Foo @Bar`) は marker ごとに 1 件ずつ collect する。
        val markerAnnotations = declaration.annotations.markerAnnotations()
        if (markerAnnotations.isEmpty()) return

        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // IrFileEntry.getLineNumber は 0-based なので +1 で 1-based に揃える (filler の design 値域)。
        val startLine = currentFile.fileEntry.getLineNumber(declaration.startOffset) + 1
        val endLine = currentFile.fileEntry.getLineNumber(declaration.endOffset) + 1
        for ((markerFqn, markerCall) in markerAnnotations) {
            val effective = effectiveConfigFor(markerFqn)
            val source = extractDeclarationSource(declaration, effective) ?: continue
            capturedSiteData += K240RcCapturedSiteData(
                site = CapturedSite(
                    markerFqn = markerFqn,
                    source = source,
                    kind = kind,
                    packageFqn = packageFqn,
                    filePath = filePath,
                    startLine = startLine,
                    endLine = endLine,
                ),
                markerCall = markerCall,
                effectiveConfig = effective,
            )
        }
    }

    /**
     * annotation list から、[CaptureCodeMarkerRegistry] に登録済みの marker FqN と、対応する
     * `IrConstructorCall` をペアで返す。
     *
     * 同じ宣言に複数 marker (`@Foo @Bar`) が付いている場合は **すべての** marker を返す。
     * 複数 marker 同時 capture (ケース #21 / #22) を可能にするため列挙する。 ユーザ定義 parameter の
     * IR 化に使うため `IrConstructorCall` 自体も保持する。
     */
    private fun List<IrConstructorCall>.markerAnnotations(): List<Pair<String, IrConstructorCall>> {
        val result = mutableListOf<Pair<String, IrConstructorCall>>()
        for (annotation in this) {
            val fqn = annotation.type.classFqName?.asString() ?: continue
            if (CaptureCodeMarkerRegistry.isMarker(fqn)) result += fqn to annotation
        }
        return result
    }

    /**
     * 宣言のソース文字列を抽出する。 Logic C (design §5.C / §7.3) + Logic D (正規化) の wire up。
     *
     * 処理ステップ:
     * 1. file text を [SourceTextExtractor.loadFileText] で取得 (PSI 経由 → file system fallback)。
     * 2. declaration の `startOffset..endOffset` から先頭の `@Marker` 行 (任意個) を
     *    [skipLeadingAnnotationLines] (offset レベル) でスキップ。Kotlin 2.0.0 の IR は
     *    declaration の startOffset に annotation 行を含む仕様への対処 (design §7.2)。
     * 3. raw substring を [normalize] に通して dedent / blank trim を適用。
     *    [NormalizeOptions] は [CaptureCodePluginConfig.toDeclarationNormalizeOptions] で
     *    config から派生する。
     *
     * 失敗条件 (`null` 返却):
     * - file text が読めない
     * - declaration の offset が UNDEFINED (-1) または不正
     * - offset が file text の範囲外
     */
    private fun extractDeclarationSource(
        declaration: IrDeclarationBase,
        effective: CaptureCodePluginConfig,
    ): String? {
        val fullText = cachedFileText ?: return null

        val rawDeclarationStart = declaration.startOffset
        val endOffset = declaration.endOffset
        if (rawDeclarationStart < 0 || endOffset < 0 || rawDeclarationStart >= endOffset) return null
        if (endOffset > fullText.length) return null

        // Kotlin 2.2+ の IR では declaration の startOffset が **`fun` / `val` 等の宣言キーワード**
        // 位置を指し、 先行する modifier (`const` / `suspend` / `inline` / `operator` / `lateinit` /
        // `private` 等) や annotation 行を含まない (K200/K210 では先頭の `@Marker` 行を含んでいた)。
        // K200/K210 baseline と同等の挙動 (modifier を含み、 marker annotation 行のみ除外) に
        // 揃えるため、 startOffset を行頭まで遡らせた上で、 直前の行が modifier-only / annotation
        // のみで構成されていればさらに前へ拡張する。 拡張後 offset は既存の `skipLeadingAnnotationLines`
        // に渡し、 marker annotation 行は token ベース skip でドロップする。
        val startOffset = expandStartToCoverModifierAndAnnotationLines(fullText, rawDeclarationStart)

        // `includeKdoc = true` (デフォルト) の場合、 declaration の startOffset の
        // 直前にある KDoc を別途抽出する。 KDoc は `@Marker` 行より手前にあるため、
        // 単純に startOffset を前方拡張すると `@Marker` 行が skip されない問題がある
        // (skipLeadingAnnotationLines は連続する `@` 行のみ skip するため、 KDoc 行で中断する)。
        // そこで KDoc 抽出と body 抽出を **分離** し、 後で連結する戦略を採る。
        val kdocPrefix = if (effective.includeKdoc) extractKdocPrefix(fullText, startOffset) else ""

        val rawStart = skipLeadingAnnotationLines(fullText, startOffset, endOffset)
        val rawBody = SourceTextExtractor.substringOrNull(fullText, rawStart, endOffset) ?: return null
        val rawText = if (kdocPrefix.isNotEmpty()) kdocPrefix + "\n" + rawBody else rawBody
        return normalize(rawText, effective.toDeclarationNormalizeOptions())
    }

    /**
     * `startOffset` を行頭まで遡らせ、 さらに直前の行が **declaration modifier のみで構成された行**
     * または **annotation 行 (`@<Name>` で始まる行)** であれば、 さらに前の行までスキャンして
     * 拡張する。 KDoc コメントブロックや declaration 本体行に到達した時点で停止する。
     *
     * Kotlin 2.2.x 以降の IR では `IrDeclaration.startOffset` が宣言キーワード (`val` / `fun` /
     * `class` / `object` / `typealias`) の位置を指し、 modifier / annotation を含まない。
     * 詳細は K220CapturedSourcesCollector の同名メソッド docstring を参照。
     */
    private fun expandStartToCoverModifierAndAnnotationLines(fullText: String, startOffset: Int): Int {
        if (startOffset <= 0 || startOffset > fullText.length) return startOffset

        // Phase 1: 同一行に modifier prefix が混じっているケース
        //          (例: `    const val MAX_RETRY = 3`、 startOffset = `val` の位置) を扱う。
        val lineStart = lineStartOffsetOf(fullText, startOffset)
        val prefix = fullText.substring(lineStart, startOffset)
        var current = if (prefix.isBlank() || prefixIsAllModifierTokens(prefix)) lineStart else startOffset

        // Phase 2: 直前の行が modifier-only 行または annotation 行 (`@<Name> ...`) であれば
        //          そこまで遡る。 Phase 1 で行頭に動かしていない場合は遡らない。
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
     * - 空行 / KDoc 装飾行 (asterisk 始まり、 ブロックコメント開始、 ブロックコメント終端) は **false** (= 拡張停止)。
     * - 行頭の非空白文字が `@` なら annotation 行として **true**。
     * - それ以外は、 行を空白で split し、 すべての token が [DECLARATION_MODIFIERS] に属する
     *   場合のみ **true**。
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

    private companion object {
        /**
         * Kotlin の declaration modifier 集合。 K200 baseline で startOffset が含んでいたが、
         * Kotlin 2.2+ では除外されるため、 これらを行レベルで吸い戻すために使う。
         */
        private val DECLARATION_MODIFIERS = setOf(
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

    /**
     * declaration の `startOffset` 直前に位置する KDoc コメントブロック (`/** ... */`) を
     * raw text として抽出する。 KDoc が見つからない場合は空文字列を返す。
     *
     * 例:
     * - `text` = `"/**\n * doc\n */\n@Marker\nfun foo()"` , startOffset = `@Marker` 行頭
     * - 戻り値 = `"/**\n * doc\n */"` (末尾 `\n` は含めない)
     */
    private fun extractKdocPrefix(fullText: String, startOffset: Int): String {
        val kdocStart = findKDocExtendedStartOffset(fullText, startOffset)
        if (kdocStart >= startOffset) return ""
        // KDoc 開始行の **行頭** (leading whitespace を含む) まで遡る。 そうすることで
        // KDoc の各行が同一の base indent を共有し、 normalize の dedent ステップが
        // body と整合的に動作する (= class 内 KDoc などインデント付きケースで誤動作しない)。
        var lineStart = kdocStart
        while (lineStart > 0 && fullText[lineStart - 1] != '\n') {
            // 行頭まで遡るのは KDoc 開始位置の直前が whitespace のときのみ
            // (= 同じ行に他のコードがある場合は遡らない)
            val ch = fullText[lineStart - 1]
            if (ch != ' ' && ch != '\t') break
            lineStart--
        }
        // lineStart..startOffset の範囲には [base indent +] KDoc 本体 + 末尾空白行 が含まれる。
        // 末尾の whitespace / newline は trim して KDoc 本体だけを返す。
        return fullText.substring(lineStart, startOffset).trimEnd()
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、 先頭の **marker annotation 行** (改行まで) を
     * スキップした offset を返す。
     *
     * 動作: かつては行頭が `@` で始まる行をすべてスキップしていたが、 `@JvmInline value class Foo(val raw: Long)`
     * のように **Kotlin 標準 annotation (`@JvmInline` 等) が宣言の意味論上必須** であるケース
     * (= inline value class) で `@JvmInline` 行まで落としてしまう問題があったため、
     * **token ベース** の skip に変更している。 marker annotation **そのもの**
     * (`@<simpleName>` + optional `(<args>)`) を token として識別し、 末尾の空白 (改行を含む) を吸収する。
     * 改行を跨いだ場合は次の行頭まで進み、 次の annotation / 宣言本体の判定に進む。
     * 同一行に declaration 本体がある場合は marker と declaration 本体の間の空白だけを吸収して停止する
     * (`@Marker val p1 = 1; @Marker val p2 = 2` のような同一行複数 property に対応)。
     *
     * 仕様:
     * - `@<simpleName>` の `<simpleName>` が [CaptureCodeMarkerRegistry] の simpleName 集合に
     *   含まれる場合のみ marker として処理し、 当該 annotation token (任意の `(...)` 引数を含む)
     *   と直後の whitespace (改行を含む) を skip。
     * - 改行を跨いだ場合、 次の行の先頭 indent は **保持** する (dedent の base として残す)。
     * - 同一行に declaration 本体がある場合、 marker と本体の間の単一スペース (or タブ) は drop。
     * - marker ではない annotation (`@JvmInline` 等) はスキップせず、 ソースの一部として残す。
     *
     * 制約: dotted FQN (`@com.example.Foo`) は simpleName 抽出が `com` になるため marker 判定が
     * 失敗し、 行が残る (= 期待値と乖離する可能性)。 現状のテストは単純名 import で書かれている
     * ため scope 外。
     */
    private fun skipLeadingAnnotationLines(text: String, startOffset: Int, endOffset: Int): Int {
        val markerSimpleNames = CaptureCodeMarkerRegistry.markerFqns
            .map { it.substringAfterLast('.') }
            .toSet()
        var cursor = startOffset
        // ループ不変条件: cursor は次の token (annotation または declaration 本体) の開始位置を
        // 「探す」ためのスキャンポインタ。 各反復で marker annotation を 1 つ消費するか、
        // 非 marker トークンを検出した時点で return する。
        while (cursor < endOffset) {
            // 1. 行頭の空白 (space / tab) をスキップして lineStart を覚える
            val lineStart = cursor
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor >= endOffset || text[cursor] != '@') {
                // 非 annotation トークン (declaration 本体 or 非 marker annotation の indent 行頭)
                return lineStart
            }
            // 2. `@<simpleName>` を解析
            val nameStart = cursor + 1
            var nameEnd = nameStart
            while (nameEnd < endOffset) {
                val ch = text[nameEnd]
                if (ch.isLetterOrDigit() || ch == '_') nameEnd++ else break
            }
            val simpleName = if (nameEnd > nameStart) text.substring(nameStart, nameEnd) else ""
            if (simpleName !in markerSimpleNames) {
                // marker でない annotation はソースの一部として残す
                return lineStart
            }
            // 3. marker annotation 本体 (`@Name`) の終端から、 もし `(` があれば balanced paren を消費
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
            // 4. annotation 直後の trailing whitespace を吸収。
            //    改行に到達した場合は改行も 1 つだけ消費し、 次の token を新しい行頭として扱う。
            //    改行に到達せず space/tab のみだった場合はそれらをすべて消費し、 同一行のまま続行
            //    (= `@Marker val p1 = 1; @Marker val p2 = 2` ケース)。
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor < endOffset && text[cursor] == '\n') {
                cursor++
            }
        }
        return cursor
    }
}
