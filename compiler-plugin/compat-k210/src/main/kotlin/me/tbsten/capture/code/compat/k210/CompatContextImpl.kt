package me.tbsten.capture.code.compat.k210

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin 2.1.x 向けの [CompatContext] 実装。
 *
 * task-028 で観察した drift を吸収する:
 *
 * - **FIR drift D1**: `FirLiteralExpression<T>` の型パラメータが 2.0.21+ で削除されたため、
 *   2.1.x では `FirLiteralExpression` (型パラメータ無し) を import して dispatch する。
 * - **FIR drift D2**: `ConeKotlinType.toRegularClassSymbol(session)` extension が
 *   `org.jetbrains.kotlin.fir.types` から `org.jetbrains.kotlin.fir.resolve` に移動。
 *   本ファイルでは後者から import する。
 * - **FIR drift D3 / D4**: `FirRegularClassSymbol.classId` accessor は 2.1.x でも安定。
 *   compat layer 経由で抽象化済。
 *
 * **IR drift D5–D8** (`putValueArgument` → `arguments` migration, `IrCallImpl` internal 化,
 * `IrConstructorCallImpl.fromSymbolOwner` 削除, `IrConst<T>` 型パラメータ削除) の対応は
 * **task-031 で実装予定**。 現時点では [transformIr] は no-op として throw する。
 * Kotlin 2.0.x 環境では ServiceLoader が [Factory.minVersion] = `2.1.0` を current
 * `2.0.0` と比較して除外するため、 本 implementation が呼び出されることはない。
 */
public class CompatContextImpl : CompatContext {

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        // TODO task-031: Kotlin 2.1.x native API で IR transform を実装する。
        //  - `IrCallImpl(...)` の internal 化 (D6) → `IrCallImplWithShape` 等の factory に切替
        //  - `IrConstructorCallImpl.fromSymbolOwner(...)` 削除 (D7) → 代替 factory
        //  - `putValueArgument(idx, expr)` → `arguments[idx] = expr` (D8)
        //  - `IrConst<T>` / `IrConstKind<T>` 型パラメータ削除 (D5)
        // 詳細は task-028 完了メモを参照。
        throw NotImplementedError(
            "CompatContextImpl (k210).transformIr is not implemented yet (task-031). " +
                "Running on Kotlin ${pluginContext.platform?.toString() ?: "?"}.",
        )
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.1.x: FirLiteralExpression は型パラメータ無し。 value は Any?。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "2.1.0"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
