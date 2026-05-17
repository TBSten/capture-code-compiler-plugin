// K2.2 で `IrElementTransformerVoid` 等が deprecated 化されたが、 既存 bytecode が
// 引き続き動くため deprecation を file 単位で抑止する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package me.tbsten.capture.code.compat.k220

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
 * K2.2+: visitor base が interface `IrElementVisitorVoid` から class `IrVisitorVoid()` に
 * 置き換わる (drift D-IR-1)。 本 class は K2.2 baseline で `IrVisitorVoid()` を継承する。
 * `visitElement` の default 実装は K2.2 でも `acceptChildrenVoid(this)` 相当だが、 explicit に
 * override しておく方が drift-safe (将来 K2.3+ で default が変わる可能性があるため)。
 */
internal class K220CallbackVisitor(
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
internal class K220CallTransformer(
    private val onCall: (IrCall) -> IrExpression?,
) : IrElementTransformerVoid() {

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed
        return onCall(transformed) ?: transformed
    }
}
