package me.tbsten.capture.code.compat.k210

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.feature.captured_sources.normalize.NormalizeOptions
import me.tbsten.capture.code.feature.captured_sources.normalize.findKDocExtendedStartOffset
import me.tbsten.capture.code.feature.captured_sources.normalize.normalize
import me.tbsten.capture.code.feature.captured_sources.normalize.toDeclarationNormalizeOptions
import me.tbsten.capture.code.feature.captured_sources.normalize.toExpressionNormalizeOptions
import me.tbsten.capture.code.feature.captured_sources.normalize.toFileNormalizeOptions
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
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * collector が収集する 1 件分のキャプチャ情報。
 *
 * 公開 API である [CapturedSite] に加えて、marker annotation 自身の [IrConstructorCall] を
 * 保持する。これにより rewriter (task-014) は call site でユーザが渡した argument
 * (例: `@Foo(id = X, label = "y")` の `X` / `"y"`) を IR 上の式として直接取り出して
 * 新しい marker instance に詰め直すことができる。
 *
 * `:compat` の `CapturedSite` 自体に IR types を晒すと API surface が広くなるため、
 * compat-k2000 内部だけで使う internal な data class として切り出している (SSOT は CapturedSite、
 * markerCall はあくまで collector → rewriter のローカル受け渡し情報)。
 */
internal data class K210CapturedSiteData(
    val site: CapturedSite,
    /**
     * `@Marker(...)` 自身に対応する IR コンストラクタ呼び出し。declaration / file 起源では
     * 必ず IR の `IrConstructorCall` が存在する (task-005 / task-016 で確認済) ので non-null だが、
     * **EXPRESSION 起源 (task-017)** では IR phase に annotation が残らない (task-009 spike) ため
     * **`null`** になる。
     *
     * rewriter は markerCall == null の場合、ユーザ定義 parameter について
     * [me.tbsten.capture.code.compat.k210.userargs.UserArgIrBuilder.buildOrDefault] の
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
     * 本 ticket scope では filler のみが入った marker (ケース #7, #67 等) を主にサポート。
     * primitive 引数を持つ marker のキャプチャは将来 task で拡張。
     */
    val expressionUserArgs: Map<String, Any?> = emptyMap(),
)

/**
 * `@CaptureCode` メタ付き marker annotation (= [CaptureCodeMarkerRegistry] に登録された FqN) を
 * 持つ宣言を IR 走査で収集する visitor。
 *
 * task-008 (Logic A) で hardcoded marker FqN list が撤廃され、本 collector は
 * [CaptureCodeMarkerRegistry] (FIR phase で動的検出された marker FqN の集合) を参照する。
 *
 * ## サポート対象 (Logic B-ir)
 *
 * task-005 では `visitProperty` のみだったが、task-012 で **宣言レベル 5 種類** に拡張した:
 *
 * | 種別 | IR ノード | visitor | [CapturedSite.CaptureKind] |
 * |------|----------|---------|----------------------------|
 * | property | [IrProperty] | [visitProperty] | [CapturedSite.CaptureKind.PROPERTY] |
 * | class / interface / annotation class | [IrClass] (`kind != OBJECT`) | [visitClass] | [CapturedSite.CaptureKind.CLASS] |
 * | object / companion | [IrClass] (`kind == OBJECT`) | [visitClass] | [CapturedSite.CaptureKind.OBJECT] |
 * | function | [IrSimpleFunction] (property accessor 除く) | [visitSimpleFunction] | [CapturedSite.CaptureKind.FUNCTION] |
 * | typealias | [IrTypeAlias] | [visitTypeAlias] | [CapturedSite.CaptureKind.TYPEALIAS] |
 *
 * task-016 で **ファイル全体** (`@file:Marker`) も収集対象に追加。`IrElementVisitorVoid` の
 * 再帰経路では `IrFile.annotations` (= file-level annotation) を訪問しないため、本 collector の
 * [collectFileAnnotations] を [K210IrInjector] パス 1 から **declaration 走査と並行で** 呼び出す
 * (visitor では拾えないので別 entry point を用意する)。
 *
 * design §4 F2 「ファイル」/ §5 Logic B 参照。
 * EXPRESSION (task-017) は後続 ticket。
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
 * (task-005 / task-009 spike で実機確認済み)。[skipLeadingAnnotationLines] で行頭 `@` 行を
 * 改行までスキップする。複数 annotation や `@JvmInline` 等の Kotlin 標準 annotation 行も
 * **すべて** スキップ (空行があれば残す) する Phase 1 ポリシー。
 * 厳密な「marker annotation のみスキップ」モードは task-018 の DSL option (`includeAnnotationLines`)
 * 配線後に検討する。
 */
internal class K210CapturedSourcesCollector(
    private val currentFile: IrFile,
    private val config: CaptureCodePluginConfig,
) : IrElementVisitorVoid {

    /**
     * 収集された capture site データ (CapturedSite + marker IrConstructorCall) のリスト。
     *
     * task-014 で公開 API `CapturedSite` に加えて marker annotation の `IrConstructorCall` も
     * 保持するようになった。後段の rewriter がユーザ定義パラメータの値を IR 上の式として
     * 取り出すために使う。
     *
     * 公開モデル ([CapturedSite]) のみを参照したい場合は [capturedSites] (compatibility view)。
     */
    val capturedSiteData: MutableList<K210CapturedSiteData> = mutableListOf()

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

    /** declaration 起源の正規化 option (task-013 で `config` の `dedent` 等を投影)。 */
    private val normalizeOptions: NormalizeOptions by lazy { config.toDeclarationNormalizeOptions() }

    /**
     * file 起源 (`@file:Marker`) の正規化 option (task-016)。
     *
     * `config.includeImports = false` (default) ならば `stripPackageAndImport = true` になり、
     * `package` 行と `import` 行が除外される。`includeImports = true` の場合はそれらも残す。
     * declaration 起源と違って `stripLeadingAnnotationLines` が `!includeAnnotationLines` で
     * 決まる点に注意 (file 起源では先頭の `@file:Marker` 行も除外する選択肢を持つ)。
     */
    private val fileNormalizeOptions: NormalizeOptions by lazy { config.toFileNormalizeOptions() }

    /**
     * EXPRESSION 起源の正規化 option (task-017)。dedent + blank trim のみ。
     * 式起源は package / import / annotation 行を含まない (FIR session が push する offset は
     * 式そのものを指す) ため、それらの strip flag は false。
     */
    private val expressionNormalizeOptions: NormalizeOptions by lazy { config.toExpressionNormalizeOptions() }

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
     * marker ごとに 1 件ずつ [CapturedSite] (`kind = FILE`) を生成する (task-016)。
     *
     * `IrElementVisitorVoid` の再帰経路では `IrFile.annotations` を訪問しないため、本 method を
     * [K210IrInjector] パス 1 から **declaration 走査とは独立に** 呼ぶ。declaration 走査と
     * file 走査を分離することで、双方の収集経路が pollute されないことを保証する。
     *
     * ## 収集仕様
     *
     * - source: file 全体テキストを [SourceTextExtractor.loadFileText] で取得し、
     *   [fileNormalizeOptions] (= `stripPackageAndImport = !includeImports`) で正規化する
     * - filePath: `IrFile.fileEntry.name` (絶対パス。task-013 と同じ妥協で Phase 2 はこのまま)
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

        val source = extractFileSource() ?: return
        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // file の終端行は `fileEntry.maxOffset` から計算する。
        // IrFileEntry.getLineNumber は 0-based なので +1 で 1-based に揃える (filler の design 値域)。
        val endLine = currentFile.fileEntry.getLineNumber(currentFile.fileEntry.maxOffset) + 1
        for ((markerFqn, markerCall) in fileAnnotations) {
            capturedSiteData += K210CapturedSiteData(
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
            )
        }
    }

    /**
     * task-017 で追加。 FIR session storage ([CaptureCodeExpressionSiteRegistry]) に push された
     * 式 annotation site のうち、本 [currentFile] にマッチするものを `CapturedSite(kind = EXPRESSION)`
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
        // (FIR checker phase の ordering 問題回避のため、 task-017 設計判断)。 IR phase 側で
        // [CaptureCodeMarkerRegistry.isMarker] による filter を行う。
        val matchingSites = CaptureCodeExpressionSiteRegistry.allSites
            .asSequence()
            .filter { CaptureCodeMarkerRegistry.isMarker(it.markerFqn) }
            .filter { it.matchesFile(filePath) }
            .sortedBy { it.startOffset }
            .toList()

        for (site in matchingSites) {
            val source = extractExpressionSource(fileText, site.startOffset, site.endOffset) ?: continue
            val startLine = currentFile.fileEntry.getLineNumber(site.startOffset) + 1
            val endLine = currentFile.fileEntry.getLineNumber(site.endOffset) + 1
            capturedSiteData += K210CapturedSiteData(
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
            )
        }
    }

    /**
     * 式 annotation 起源の raw 抽出 + 正規化。
     *
     * FIR session が push する `(startOffset, endOffset)` は **`@Marker (expr)` のうちの「式部分」**
     * (task-009 spike (c) より、annotation 自体ではなく対象 expression の source range)。
     * ただし spike 観察と production の挙動には僅かに差異がある可能性があるため、
     * 抽出後に **両端の `(` `)` をペアでひと組ずつ strip** する補正を行う。これにより
     * `(1 + 2 + 3)` で push されても `1 + 2 + 3` に揃う。
     *
     * `@Marker run { ... }` (ケース #29) や `@Marker ({ ... })` (ケース #30) では range が
     * lambda / parenthesized expression 全体を覆うため、 strip は **両端が対応する場合のみ** 一度。
     * `({ x })` のような特殊形は strip しない (= `({ x })` のまま保つ)。 これは spike (f) で
     * design §3.4 / §7.8 「`run { }` 推奨」の方針と整合する。
     */
    private fun extractExpressionSource(fullText: String, startOffset: Int, endOffset: Int): String? {
        val raw = SourceTextExtractor.substringOrNull(fullText, startOffset, endOffset) ?: return null
        val stripped = stripSurroundingParens(raw)
        return normalize(stripped, expressionNormalizeOptions)
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
     * ## marker class 自身の declaration 除外 (task-016)
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
    private fun extractFileSource(): String? {
        val fullText = cachedFileText ?: return null
        val withoutMarkers = stripMarkerClassDeclarations(fullText)
        return normalize(withoutMarkers, fileNormalizeOptions)
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
     * task-013 でこの method は **location 情報** (`packageFqn` / `filePath` / `startLine` /
     * `endLine`) も合わせて埋めるようになった。後段 ([K210CapturedSourcesRewriter]) で
     * `SourceLocation` filler に注入される。
     */
    private fun collectIfMarked(
        declaration: IrDeclarationBase,
        kind: CapturedSite.CaptureKind,
    ) {
        // task-014 で marker annotation の `IrConstructorCall` も合わせて取り出すように変更。
        // 1 宣言に複数 marker が付いている場合 (`@Foo @Bar`) は marker ごとに 1 件ずつ collect する。
        val markerAnnotations = declaration.annotations.markerAnnotations()
        if (markerAnnotations.isEmpty()) return

        val source = extractDeclarationSource(declaration) ?: return
        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // IrFileEntry.getLineNumber は 0-based なので +1 で 1-based に揃える (filler の design 値域)。
        val startLine = currentFile.fileEntry.getLineNumber(declaration.startOffset) + 1
        val endLine = currentFile.fileEntry.getLineNumber(declaration.endOffset) + 1
        for ((markerFqn, markerCall) in markerAnnotations) {
            capturedSiteData += K210CapturedSiteData(
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
            )
        }
    }

    /**
     * annotation list から、[CaptureCodeMarkerRegistry] に登録済みの marker FqN と、対応する
     * `IrConstructorCall` をペアで返す。
     *
     * 同じ宣言に複数 marker (`@Foo @Bar`) が付いている場合は **すべての** marker を返す。
     * task-005 では「最初に見つかった 1 つだけ」を返す実装だったが、task-012 で複数 marker
     * 同時 capture (ケース #21 / #22) を可能にするため列挙に変更。task-014 で `IrConstructorCall`
     * 自体も保持する形に拡張 (ユーザ定義 parameter の IR 化に必要)。
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
     * 宣言のソース文字列を抽出する。Logic C (design §5.C / §7.3) + Logic D (task-015) の wire up。
     *
     * 処理ステップ:
     * 1. file text を [SourceTextExtractor.loadFileText] で取得 (PSI 経由 → file system fallback)。
     * 2. declaration の `startOffset..endOffset` から先頭の `@Marker` 行 (任意個) を
     *    [skipLeadingAnnotationLines] (offset レベル) でスキップ。Kotlin 2.0.0 の IR は
     *    declaration の startOffset に annotation 行を含む仕様への対処 (design §7.2)。
     * 3. raw substring を [normalize] に通して dedent / blank trim を適用 (task-013 で wire up)。
     *    [NormalizeOptions] は [CaptureCodePluginConfig.toDeclarationNormalizeOptions] で
     *    config から派生する。
     *
     * 失敗条件 (`null` 返却):
     * - file text が読めない
     * - declaration の offset が UNDEFINED (-1) または不正
     * - offset が file text の範囲外
     */
    private fun extractDeclarationSource(declaration: IrDeclarationBase): String? {
        val fullText = cachedFileText ?: return null

        val startOffset = declaration.startOffset
        val endOffset = declaration.endOffset
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
        if (endOffset > fullText.length) return null

        // task-042: `includeKdoc = true` (デフォルト) の場合、 declaration の startOffset の
        // 直前にある KDoc を別途抽出する。 KDoc は `@Marker` 行より手前にあるため、
        // 単純に startOffset を前方拡張すると `@Marker` 行が skip されない問題がある
        // (skipLeadingAnnotationLines は連続する `@` 行のみ skip するため、 KDoc 行で中断する)。
        // そこで KDoc 抽出と body 抽出を **分離** し、 後で連結する戦略を採る。
        val kdocPrefix = if (config.includeKdoc) extractKdocPrefix(fullText, startOffset) else ""

        val rawStart = skipLeadingAnnotationLines(fullText, startOffset, endOffset)
        val rawBody = SourceTextExtractor.substringOrNull(fullText, rawStart, endOffset) ?: return null
        val rawText = if (kdocPrefix.isNotEmpty()) kdocPrefix + "\n" + rawBody else rawBody
        return normalize(rawText, normalizeOptions)
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
     * task-041 で挙動を変更: かつては行頭が `@` で始まる行をすべてスキップしていたが、
     * `@JvmInline value class Foo(val raw: Long)` のように **Kotlin 標準 annotation
     * (`@JvmInline` 等) が宣言の意味論上必須** であるケース (= inline value class) で
     * `@JvmInline` 行まで落としてしまい、 結果 source として残らない問題があった。
     *
     * task-043 で **token ベース** に変更: 旧実装は「marker 行を改行まで丸ごと飲み込む」線形 skip
     * だったが、 `@Marker val p1 = 1; @Marker val p2 = 2` のような **同一行に複数 property** 形式では
     * 改行まで進めると `endOffset` を超え substring が null を返してしまっていた (= capture 失敗)。
     * 新実装は marker annotation **そのもの** (`@<simpleName>` + optional `(<args>)`) を token として
     * 識別し、 末尾の空白 (改行を含む) を吸収する。 改行を跨いだ場合は次の行頭まで進み、 次の
     * annotation / 宣言本体の判定に進む。 同一行に declaration 本体がある場合は marker と
     * declaration 本体の間の空白だけを吸収して停止する。
     *
     * 新仕様:
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
