package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.compat.CapturedSite
import me.tbsten.capture.code.feature.captured_sources.normalize.NormalizeOptions
import me.tbsten.capture.code.feature.captured_sources.normalize.normalize
import me.tbsten.capture.code.feature.captured_sources.normalize.toDeclarationNormalizeOptions
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
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

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
 * design §4 F2 「宣言レベル」/ §5 Logic B-ir 参照。
 * EXPRESSION (task-017) / FILE (task-016) は後続 ticket。
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

    val capturedSites: MutableList<CapturedSite> = mutableListOf()

    /** 同一 [IrFile] に対する複数の declaration を処理する際に file テキストをキャッシュする。 */
    private val cachedFileText: String? by lazy { SourceTextExtractor.loadFileText(currentFile) }

    /** declaration 起源の正規化 option (task-013 で `config` の `dedent` 等を投影)。 */
    private val normalizeOptions: NormalizeOptions by lazy { config.toDeclarationNormalizeOptions() }

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
        val markers = declaration.annotations.markerFqns()
        if (markers.isEmpty()) return

        val source = extractDeclarationSource(declaration) ?: return
        val packageFqn = currentFile.packageFqName.asString()
        val filePath = currentFile.fileEntry.name
        // IrFileEntry.getLineNumber は 0-based なので +1 で 1-based に揃える (filler の design 値域)。
        val startLine = currentFile.fileEntry.getLineNumber(declaration.startOffset) + 1
        val endLine = currentFile.fileEntry.getLineNumber(declaration.endOffset) + 1
        for (markerFqn in markers) {
            capturedSites += CapturedSite(
                markerFqn = markerFqn,
                source = source,
                kind = kind,
                packageFqn = packageFqn,
                filePath = filePath,
                startLine = startLine,
                endLine = endLine,
            )
        }
    }

    /**
     * annotation list から、[CaptureCodeMarkerRegistry] に登録済みの marker FqN を返す。
     *
     * 同じ宣言に複数 marker (`@Foo @Bar`) が付いている場合は **すべての** marker FqN を返す。
     * task-005 では「最初に見つかった 1 つだけ」を返す実装だったが、task-012 で複数 marker
     * 同時 capture (ケース #21 / #22) を可能にするため列挙に変更。
     */
    private fun List<IrConstructorCall>.markerFqns(): List<String> {
        val result = mutableListOf<String>()
        for (annotation in this) {
            val fqn = annotation.type.classFqName?.asString() ?: continue
            if (CaptureCodeMarkerRegistry.isMarker(fqn)) result += fqn
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
