// K2.3 でも `IrElementTransformerVoid` 等が deprecated 化されている。 引き続き bytecode 互換のため
// deprecation を file 単位で抑止する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package me.tbsten.capture.code.compat.k230

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Callback-driven IR visitor used by `CompatContextImpl.walkIrTree` /
 * `walkIrFileDeclarations` (task-120-B Phase 2)。
 *
 * K2.3 baseline: K2.2 と同様 `IrVisitorVoid()` class を継承。 visitor base drift D-IR-1
 * は本 module 時点では interface → class の置換は完了済 (= class form)。
 */
internal class K230CallbackVisitor(
    private val onClass: (IrClass) -> Unit,
    private val onSimpleFunction: (IrSimpleFunction) -> Unit,
    private val onProperty: (IrProperty) -> Unit,
    private val onTypeAlias: (IrTypeAlias) -> Unit,
) : IrVisitorVoid() {

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
internal class K230CallTransformer(
    private val onCall: (IrCall) -> IrExpression?,
) : IrElementTransformerVoid() {

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed
        return onCall(transformed) ?: transformed
    }
}
