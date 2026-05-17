// K2.4-RC でも `IrElementTransformerVoid` 等が deprecated 化されている。 bytecode 互換のため
// deprecation を file 単位で抑止する。
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package me.tbsten.capture.code.compat.k240rc

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
 * K2.4-RC: visitor base は K2.2+ で導入された `IrVisitorVoid()` class。 `IrElementVisitorVoid`
 * interface は依然 deprecated form として残るが、 K2.4-RC では `IrVisitorVoid` を使う方が
 * future-proof (K2.5 でついに interface 形態が削除される見込み)。
 */
internal class K240RcCallbackVisitor(
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
internal class K240RcCallTransformer(
    private val onCall: (IrCall) -> IrExpression?,
) : IrElementTransformerVoid() {

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression)
        if (transformed !is IrCall) return transformed
        return onCall(transformed) ?: transformed
    }
}
