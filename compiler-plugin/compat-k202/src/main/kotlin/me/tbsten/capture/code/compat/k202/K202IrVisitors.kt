package me.tbsten.capture.code.compat.k202

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Callback-driven IR visitor used by `CompatContextImpl.walkIrTree` /
 * `walkIrFileDeclarations` (task-120-B Phase 2).
 *
 * K2.0.21 baseline では `IrElementVisitorVoid` は K2.0 と同様 interface 形態。
 * 構造は `K200CallbackVisitor` と同一 (module 跨ぎ class 衝突回避のため別 file)。
 */
internal class K202CallbackVisitor(
    private val onClass: (IrClass) -> Unit,
    private val onSimpleFunction: (IrSimpleFunction) -> Unit,
    private val onProperty: (IrProperty) -> Unit,
    private val onTypeAlias: (IrTypeAlias) -> Unit,
) : IrElementVisitorVoid {

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        onClass(declaration)
        declaration.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        onSimpleFunction(declaration)
        declaration.acceptChildrenVoid(this)
    }

    override fun visitProperty(declaration: IrProperty) {
        onProperty(declaration)
        declaration.acceptChildrenVoid(this)
    }

    override fun visitTypeAlias(declaration: IrTypeAlias) {
        onTypeAlias(declaration)
        declaration.acceptChildrenVoid(this)
    }
}

/**
 * Callback-driven IR transformer used by
 * `CompatContextImpl.transformCallsInModule` (task-120-B Phase 2)。
 */
internal class K202CallTransformer(
    private val onCall: (IrCall) -> IrExpression?,
) : IrElementTransformerVoid() {

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed
        return onCall(transformed) ?: transformed
    }
}
