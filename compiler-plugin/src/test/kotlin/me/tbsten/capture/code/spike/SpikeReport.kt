package me.tbsten.capture.code.spike

/**
 * Expression annotation spike の観察結果を蓄積するための SSOT データ構造。
 *
 * IR phase / FIR phase の両方の観察者が単一の [SpikeReport] にレコードを push し、
 * 最後にテストドライバが [toMarkdown] でフォーマットして `.local/tmp/...` に書き出す。
 *
 * 本 spike は production code には組み込まない (test only) ため、threadsafety は考慮しない。
 * kctfork の 1 compile = 1 report で利用する。
 */
internal class SpikeReport(
    val caseName: String,
) {
    /** FIR phase で観測した annotation のレコード。複数あれば順に push される。 */
    val firAnnotations: MutableList<FirAnnotationRecord> = mutableListOf()

    /** IR phase で観測した「式 annotation」 (= 宣言以外に付いた annotation) のレコード。 */
    val irExpressionAnnotations: MutableList<IrExpressionAnnotationRecord> = mutableListOf()

    /** IR phase で観測した「宣言 annotation」 (= property / function 等) のレコード。 */
    val irDeclarationAnnotations: MutableList<IrDeclarationAnnotationRecord> = mutableListOf()

    /** PsiIrFileEntry / NaiveSourceBasedFileEntry のどちらが使われたか。 */
    var fileEntryClassName: String? = null

    /** `IrFileEntry.getSourceRangeInfo` の戻り値の例 (最初の式 annotation のもの)。 */
    var firstSourceRangeInfoDump: String? = null

    /** PsiIrFileEntry の psiFile.text 全文が取れたか (取れた場合は length のみ)。 */
    var psiTextAvailableLength: Int? = null

    /** PsiIrFileEntry の psiFile.text を annotation 領域で substring した結果のサンプル。 */
    var substringSample: String? = null

    fun toMarkdown(): String = buildString {
        appendLine("### Case: $caseName")
        appendLine()
        appendLine("- FileEntry class: `${fileEntryClassName ?: "(unknown)"}`")
        appendLine("- PSI text length: ${psiTextAvailableLength ?: "(N/A)"}")
        appendLine()
        appendLine("#### FIR observations (`FirAnnotationCallChecker`)")
        if (firAnnotations.isEmpty()) {
            appendLine("- (no annotations observed at FIR phase)")
        } else {
            firAnnotations.forEach { rec ->
                appendLine(
                    "- fqn=`${rec.classFqn}` " +
                        "containingDecl=`${rec.containingDeclaration}` " +
                        "psiText=`${rec.psiText.escapeBackticks()}` " +
                        "offsets=(${rec.startOffset}..${rec.endOffset}) " +
                        "line=${rec.startLine}",
                )
            }
        }
        appendLine()
        appendLine("#### IR observations: declaration annotations")
        if (irDeclarationAnnotations.isEmpty()) {
            appendLine("- (no declaration annotations observed at IR phase)")
        } else {
            irDeclarationAnnotations.forEach { rec ->
                appendLine(
                    "- decl=`${rec.declarationKind} ${rec.declarationName}` " +
                        "annFqn=`${rec.annotationFqn}` " +
                        "declRange=(${rec.declarationStart}..${rec.declarationEnd}) " +
                        "annRange=(${rec.annotationStart}..${rec.annotationEnd}) " +
                        "declLine=${rec.declarationStartLine} annLine=${rec.annotationStartLine}",
                )
            }
        }
        appendLine()
        appendLine("#### IR observations: expression annotations (annotation on IrExpression)")
        if (irExpressionAnnotations.isEmpty()) {
            appendLine("- (NONE — annotation was dropped before/during IR generation)")
        } else {
            irExpressionAnnotations.forEach { rec ->
                appendLine(
                    "- exprClass=`${rec.expressionClass}` " +
                        "annFqn=`${rec.annotationFqn}` " +
                        "exprRange=(${rec.expressionStart}..${rec.expressionEnd}) " +
                        "annRange=(${rec.annotationStart}..${rec.annotationEnd}) " +
                        "exprLine=${rec.expressionStartLine} " +
                        "extractedText=`${rec.extractedText.escapeBackticks()}`",
                )
            }
        }
        if (firstSourceRangeInfoDump != null) {
            appendLine()
            appendLine("#### `IrFileEntry.getSourceRangeInfo` (first annotation)")
            appendLine("```")
            appendLine(firstSourceRangeInfoDump)
            appendLine("```")
        }
        if (substringSample != null) {
            appendLine()
            appendLine("#### Substring sample from `PsiIrFileEntry.psiFile.text`")
            appendLine("```")
            appendLine(substringSample)
            appendLine("```")
        }
    }

    private fun String.escapeBackticks(): String = replace("`", "\\`")
}

/** FIR phase で `FirAnnotationCallChecker` が観測した 1 件分の情報。 */
internal data class FirAnnotationRecord(
    val classFqn: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val containingDeclaration: String,
    val psiText: String,
)

/** IR phase で観測した宣言 annotation の 1 件分。 */
internal data class IrDeclarationAnnotationRecord(
    val declarationKind: String,
    val declarationName: String,
    val annotationFqn: String,
    val declarationStart: Int,
    val declarationEnd: Int,
    val annotationStart: Int,
    val annotationEnd: Int,
    val declarationStartLine: Int,
    val annotationStartLine: Int,
)

/** IR phase で観測した式 annotation の 1 件分 ([org.jetbrains.kotlin.ir.expressions.IrExpression] に付いたもの)。 */
internal data class IrExpressionAnnotationRecord(
    val expressionClass: String,
    val annotationFqn: String,
    val expressionStart: Int,
    val expressionEnd: Int,
    val annotationStart: Int,
    val annotationEnd: Int,
    val expressionStartLine: Int,
    val extractedText: String,
)
