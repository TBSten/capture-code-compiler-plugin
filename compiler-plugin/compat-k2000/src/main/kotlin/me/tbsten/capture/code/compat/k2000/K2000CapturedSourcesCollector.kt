package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.feature.captured_sources.normalize.NormalizeOptions
import me.tbsten.capture.code.feature.captured_sources.normalize.normalize
import me.tbsten.capture.code.feature.captured_sources.normalize.toDeclarationNormalizeOptions
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
internal data class K2000CapturedSiteData(
    val site: CapturedSite,
    /**
     * `@Marker(...)` 自身に対応する IR コンストラクタ呼び出し。
     *
     * これを通じてユーザが call site で渡した argument (filler 以外) を `getValueArgument(i)` で
     * 取り出す。call site で省略 (default 利用) されている場合は `null` が返るので、その時は
     * marker class の primary constructor の `valueParameters[i].defaultValue` を見る。
     */
    val markerCall: IrConstructorCall,
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
 * [collectFileAnnotations] を [K2000IrInjector] パス 1 から **declaration 走査と並行で** 呼び出す
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
internal class K2000CapturedSourcesCollector(
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
    val capturedSiteData: MutableList<K2000CapturedSiteData> = mutableListOf()

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
     * [K2000IrInjector] パス 1 から **declaration 走査とは独立に** 呼ぶ。declaration 走査と
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
            capturedSiteData += K2000CapturedSiteData(
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
     * `endLine`) も合わせて埋めるようになった。後段 ([K2000CapturedSourcesRewriter]) で
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
            capturedSiteData += K2000CapturedSiteData(
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

        val rawStart = skipLeadingAnnotationLines(fullText, startOffset, endOffset)
        val rawText = SourceTextExtractor.substringOrNull(fullText, rawStart, endOffset) ?: return null
        return normalize(rawText, normalizeOptions)
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、先頭の `@Foo` アノテーション行 (改行まで) を
     * スキップした offset を返す。
     *
     * Phase 1 / task-005 ではシンプルさのため行頭に `@` がある限り次の改行までスキップする
     * 線形パスとしている。複数 annotation や複数行 annotation 引数には対応する
     * (1 行ずつ判定する) が、annotation arguments 内に改行がある場合 (`@Foo(\n  ...\n)`) には
     * 未対応 (現状のテストケースは 1 行 annotation 引数のみ)。
     *
     * 行頭の空白はスキップ前にカウントし、`@` で始まらない行ならその行頭 (空白含む) を返す。
     */
    private fun skipLeadingAnnotationLines(text: String, startOffset: Int, endOffset: Int): Int {
        var cursor = startOffset
        while (cursor < endOffset) {
            // 行頭の空白をスキップ
            val lineStart = cursor
            while (cursor < endOffset && (text[cursor] == ' ' || text[cursor] == '\t')) {
                cursor++
            }
            if (cursor >= endOffset || text[cursor] != '@') {
                // アノテーション行ではないので、行頭の空白を含めて return
                return lineStart
            }
            // `@` を含む行末まで進める (annotation arguments 内に改行が無い前提)
            while (cursor < endOffset && text[cursor] != '\n') {
                cursor++
            }
            // 改行をスキップ
            if (cursor < endOffset && text[cursor] == '\n') {
                cursor++
            }
        }
        return cursor
    }
}
