package me.tbsten.capture.code.spike

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.PsiIrFileEntry
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * task-009 spike: IR phase での観察用 [IrGenerationExtension]。
 *
 * production code には流さない (test 専用)。observation を [SpikeReport] に push する。
 *
 * 主な観察対象:
 * 1. [IrFile] の `fileEntry` が [PsiIrFileEntry] か否か
 * 2. 全 declaration の `annotations: List<IrConstructorCall>` を走査し、`@CaptureExpr` 等の
 *    marker と思しき annotation が残っているかを記録 (= R1 の declaration 側裏付け)
 * 3. 全 [IrExpression] を走査し、annotation が残っているかを記録 (= R1 の expression 側裏付け)
 * 4. annotation の startOffset / endOffset が source 上どこを指しているかを記録 (R2 検証)
 * 5. PsiIrFileEntry の `psiFile.text.substring(s, e)` が引けるかを 1 件サンプル
 *
 * Kotlin 2.0 の [IrExpression] は [IrAnnotationContainer] を **継承していない** ため、
 * 通常の経路では IR phase で式 annotation を観測できない。本観察 extension は
 * `IrElementVisitorVoid` で全ノードを訪問するが、`annotations` を読めるのは
 * `IrAnnotationContainer` 経由のみ。よって `irExpressionAnnotations` は通常空のままになる
 * (= R1 が肯定される)。これも観察結果として記録する。
 */
internal class SpikeIrExtension(
    private val report: SpikeReport,
    /** marker FqN として観測対象にしたい annotation のリスト。これ以外は無視する。 */
    private val markerFqns: Set<String>,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { observeFile(it) }
    }

    private fun observeFile(irFile: IrFile) {
        val entry = irFile.fileEntry
        report.fileEntryClassName = entry::class.qualifiedName
        val psiEntry = entry as? PsiIrFileEntry
        if (psiEntry != null) {
            val text = psiEntry.psiFile.text
            report.psiTextAvailableLength = text.length
        }
        irFile.acceptChildrenVoid(SpikeIrVisitor(irFile, report, markerFqns))
    }
}

private class SpikeIrVisitor(
    private val irFile: IrFile,
    private val report: SpikeReport,
    private val markerFqns: Set<String>,
) : IrElementVisitorVoid {

    override fun visitElement(element: IrElement) {
        // IrExpression に annotation が残るか観察 (Kotlin 2.0 では基本的に残らない見込み)
        if (element is IrExpression && element is IrAnnotationContainer) {
            recordExpressionAnnotations(element)
        }
        // declaration の annotation 観察
        if (element is IrDeclarationBase) {
            recordDeclarationAnnotations(element)
        }
        element.acceptChildrenVoid(this)
    }

    private fun recordDeclarationAnnotations(declaration: IrDeclarationBase) {
        val kindLabel = declarationKindLabel(declaration)
        val name = declarationName(declaration)
        declaration.annotations.forEach { ann ->
            val fqn = ann.type.classFqName?.asString() ?: return@forEach
            if (fqn !in markerFqns) return@forEach
            report.irDeclarationAnnotations += IrDeclarationAnnotationRecord(
                declarationKind = kindLabel,
                declarationName = name,
                annotationFqn = fqn,
                declarationStart = declaration.startOffset,
                declarationEnd = declaration.endOffset,
                annotationStart = ann.startOffset,
                annotationEnd = ann.endOffset,
                declarationStartLine = lineOf(declaration.startOffset),
                annotationStartLine = lineOf(ann.startOffset),
            )
        }
    }

    private fun recordExpressionAnnotations(expression: IrExpression) {
        val container = expression as IrAnnotationContainer
        val anns: List<IrConstructorCall> = container.annotations
        anns.forEach { ann ->
            val fqn = ann.type.classFqName?.asString() ?: return@forEach
            if (fqn !in markerFqns) return@forEach
            val extracted = runCatching {
                val psi = (irFile.fileEntry as? PsiIrFileEntry)?.psiFile?.text
                if (psi != null && expression.startOffset >= 0 && expression.endOffset >= 0) {
                    psi.substring(expression.startOffset, expression.endOffset)
                } else {
                    "(no PSI text)"
                }
            }.getOrElse { "(extract-failed: ${it::class.simpleName})" }
            report.irExpressionAnnotations += IrExpressionAnnotationRecord(
                expressionClass = expression::class.simpleName ?: expression::class.java.name,
                annotationFqn = fqn,
                expressionStart = expression.startOffset,
                expressionEnd = expression.endOffset,
                annotationStart = ann.startOffset,
                annotationEnd = ann.endOffset,
                expressionStartLine = lineOf(expression.startOffset),
                extractedText = extracted,
            )
            // 最初の 1 件で getSourceRangeInfo と substring sample を記録
            if (report.firstSourceRangeInfoDump == null) {
                val info = runCatching {
                    irFile.fileEntry.getSourceRangeInfo(expression.startOffset, expression.endOffset)
                }.getOrNull()
                report.firstSourceRangeInfoDump = info?.toString() ?: "(unavailable)"
                report.substringSample = extracted
            }
        }
    }

    private fun lineOf(offset: Int): Int {
        if (offset < 0) return -1
        return irFile.fileEntry.getLineNumber(offset) + 1 // 1-based
    }

    private fun declarationKindLabel(declaration: IrDeclaration): String = when (declaration) {
        is IrProperty -> "property"
        is IrFunction -> "function"
        is IrClass -> "class"
        is IrTypeAlias -> "typealias"
        else -> declaration::class.simpleName ?: "decl"
    }

    private fun declarationName(declaration: IrDeclaration): String = when (declaration) {
        is IrProperty -> declaration.name.asString()
        is IrFunction -> declaration.name.asString()
        is IrClass -> declaration.name.asString()
        is IrTypeAlias -> declaration.name.asString()
        else -> declaration::class.simpleName ?: "?"
    }
}
