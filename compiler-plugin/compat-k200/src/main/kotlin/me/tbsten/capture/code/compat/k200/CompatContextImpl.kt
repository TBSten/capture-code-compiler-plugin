package me.tbsten.capture.code.compat.k200

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin 2.0.x 向けの [CompatContext] 実装。
 *
 * 単一の Metro pattern interface (`CompatContext`) に統合された結果、 IR 変換と FIR
 * 補助メソッドの両方を本 class が実装する。
 *
 * - [transformIr] は [runK200IrTransform] (top-level 関数) に委譲。
 * - [literalValueOrNull] / [isLiteralExpression] は 2.0.0 ベースの
 *   `FirLiteralExpression<*>` (型パラメータ付き) を dispatch して `value` を読み出す。
 *   Kotlin 2.0.21+ では型パラメータが削除されているため、 同等処理を [k210][CompatContext]
 *   実装側で 2.1.0 native API で書き直す。
 * - [toRegularClassSymbolOrNull] は `org.jetbrains.kotlin.fir.types.toRegularClassSymbol`
 *   (Kotlin 2.0.x の package パス) を import して dispatch する。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 */
public class CompatContextImpl : CompatContext {

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        runK200IrTransform(moduleFragment, pluginContext, config)
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.0.x の FirLiteralExpression は `<T>` 型パラメータ付き。
        // 2.0.21+ では型パラメータが削除されているため、 ここでは 2.0.0 native の `<*>`
        // を素直に dispatch し、 後続バージョンは別 compat module 側で再実装する。
        return (expression as? FirLiteralExpression<*>)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression<*>

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "2.0.0"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
