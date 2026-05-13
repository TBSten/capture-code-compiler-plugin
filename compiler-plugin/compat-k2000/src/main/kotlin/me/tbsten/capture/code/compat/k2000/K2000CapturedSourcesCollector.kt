package me.tbsten.capture.code.compat.k2000

import me.tbsten.capture.code.compat.CapturedSite
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import java.io.File

/**
 * hardcoded marker (`com.example.Snippets` ほか [HARDCODED_MARKER_FQNS]) 付き property 宣言を
 * IR 走査で収集する visitor。
 *
 * Phase 1 vertical slice (task-005) の Logic B / C 最小実装:
 * - Logic B (ターゲットノード収集) … `visitProperty` で annotation の FqN match を検査
 * - Logic C (ソーステキスト取得) … property の `startOffset..endOffset` を抽出し、
 *   [PsiIrFileEntry] (PSI 経由) または file system path (PSI 無し) からソース文字列を取り出す
 *
 * 収集結果は [capturedSites] に積まれ、後続 task-006 で `capturedSources<T>()` 呼び出しの
 * 書き換えに利用される。
 *
 * Phase 2 で Logic A (メタアノテーション動的検出) に置き換えられた時点で [HARDCODED_MARKER_FQNS] と
 * 本 collector の hardcoded path は撤廃される予定。
 */
internal class K2000CapturedSourcesCollector(
    private val currentFile: IrFile,
) : IrElementVisitorVoid {

    val capturedSites: MutableList<CapturedSite> = mutableListOf()

    /** 同一 [IrFile] に対する複数の declaration を処理する際に file テキストをキャッシュする。 */
    private val cachedFileText: String? by lazy { loadFileText(currentFile) }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitProperty(declaration: IrProperty) {
        val markerFqn = declaration.annotations.firstHardcodedMarkerFqnOrNull()
        if (markerFqn != null) {
            val source = extractPropertySource(declaration)
            if (source != null) {
                capturedSites += CapturedSite(
                    markerFqn = markerFqn,
                    source = source,
                )
            }
        }
        super.visitProperty(declaration)
    }

    private fun List<IrConstructorCall>.firstHardcodedMarkerFqnOrNull(): String? {
        for (annotation in this) {
            val fqn = annotation.type.classFqName?.asString() ?: continue
            if (fqn in HARDCODED_MARKER_FQNS) return fqn
        }
        return null
    }

    /**
     * property 宣言のソース文字列を抽出する。
     *
     * Logic C (design §5.C / §7.3) の最小実装。`IrFileEntry.getSourceRangeInfo` は行列情報のみで
     * 生テキストを保持しないため、生テキストの取得は次の優先順位で行う:
     *
     * 1. [PsiIrFileEntry] が利用可能ならその PSI file text から substring
     * 2. PSI が無い (LightTree / [org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl] など)
     *    場合は [IrFileEntry.name] が指す絶対パスからファイルを読み直す (kctfork test 経由でも
     *    sources は一時 dir に書き出されるためアクセス可能)
     *
     * アノテーション行の除外は declaration の `startOffset` がアノテーション領域を含む場合に
     * 行末改行までスキップする (design §7.2)。Kotlin 2.0.0 の挙動では IR `startOffset` は
     * 先頭アノテーションを含む位置になることを実機で確認している。
     */
    private fun extractPropertySource(declaration: IrProperty): String? {
        val fullText = cachedFileText ?: return null

        val startOffset = declaration.startOffset
        val endOffset = declaration.endOffset
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
        if (endOffset > fullText.length) return null

        val rawStart = skipLeadingAnnotationLines(fullText, startOffset, endOffset)
        return fullText.substring(rawStart, endOffset)
    }

    private fun loadFileText(file: IrFile): String? {
        // 第一選択: PSI が利用可能ならその text を使う (offset 整合が確実)
        (file.fileEntry as? PsiIrFileEntry)?.psiFile?.text?.let { return it }
        // フォールバック: file entry の name が file system 上のパスを指す場合はそれを読む
        val path = file.fileEntry.name
        val candidate = File(path)
        if (!candidate.isFile) return null
        return runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
    }

    /**
     * `startOffset` 〜 `endOffset` の範囲のうち、先頭の `@Foo` アノテーション行 (改行まで) を
     * スキップした offset を返す。
     *
     * Phase 1 では実装をシンプルに保つため、行頭に `@` がある限り次の改行までスキップする
     * 線形パスを採る。複数 annotation や複数行 annotation には未対応 (Phase 2 で対応)。
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

    internal companion object {
        // TODO: Phase 2 task 2.1 で Logic A (メタアノテーション動的検出) に置換し、本 List は撤廃する。
        // 現在は Phase 1 同期ポイント (task-007) のため、ケース #1 enable 用の marker を追加した。
        // - `com.example.Snippets` : `:compiler-plugin:test` 内の kctfork ベース unit test (task-005/006)
        // - `me.tbsten.capture.code.testapp.Snippets_Case1` : `:integration-test:test-jvm` BasicCasesTest ケース #1
        val HARDCODED_MARKER_FQNS: List<String> = listOf(
            "com.example.Snippets",
            "me.tbsten.capture.code.testapp.Snippets_Case1",
        )
    }
}
