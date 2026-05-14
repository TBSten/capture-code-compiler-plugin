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
 * K2.0 系からの drift を吸収する:
 *
 * - **FIR drift D1**: `FirLiteralExpression<T>` の型パラメータが 2.0.21+ で削除されたため、
 *   2.1.x では `FirLiteralExpression` (型パラメータ無し) を import して dispatch する。
 * - **FIR drift D2**: `ConeKotlinType.toRegularClassSymbol(session)` extension が
 *   `org.jetbrains.kotlin.fir.types` から `org.jetbrains.kotlin.fir.resolve` に移動。
 *   本ファイルでは後者から import する。
 * - **FIR drift D3 / D4**: `FirRegularClassSymbol.classId` accessor は 2.1.x でも安定。
 *   compat layer 経由で抽象化済。
 *
 * **IR drift D5–D8**:
 *
 * - **D5**: `IrConst<T>` / `IrConstKind<T>` の型パラメータが 2.1.0 で削除された。 [transformIr]
 *   から呼ばれる [me.tbsten.capture.code.compat.k210.userargs.UserArgPrimitiveIrBuilder] は
 *   2.1.0 native の `IrConst` (非ジェネリック) / `IrConstKind` (非ジェネリック) を使う。
 * - **D6 / D7**: 2.1.0 公開版 (`2.1.0` final) では `IrCallImpl(...)` constructor は internal 化
 *   されたが、 同名の **top-level factory 関数** が `org.jetbrains.kotlin.ir.expressions.impl`
 *   package に追加されており、 既存 call 形 (位置引数 + apply ブロック) はそのまま compile できる
 *   (ただし `valueArgumentsCount` 引数は無く、 symbol.owner.valueParameters.size から自動で
 *   初期化される)。 `IrConstructorCallImpl.Companion.fromSymbolOwner(...)` も Companion メソッド
 *   から **Companion 拡張関数** に格上げされたため、 `import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner`
 *   を追加すれば同じ呼び出し形を維持できる。
 * - **D8**: `putValueArgument(index, expr)` / `putTypeArgument(index, type)` は 2.1.0 でも
 *   `IrMemberAccessExpression` の public final method として **残存** している (`final void
 *   putValueArgument(int, IrExpression)`)。 K2.0 系 dev build で観察された "argument MutableList
 *   migration" は 2.1.0 final 時点では未投入のため、 K200 と同じコードがそのまま使える。
 *
 * 結論として、 `compat-k210` の IR transform を **K200 とほぼ同型に保ち**、
 * **D5 の `IrConstKind` 型パラメータ削除** と **D6/D7 の factory 切り替え** (top-level
 * builder + Companion 拡張 import) を個別に吸収する形で実装した。 万一 2.1.x の patch release で
 * D8 (`putValueArgument` 削除等) が投入された場合は本 module 内のみで再対応する。
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
        runK210IrTransform(moduleFragment, pluginContext, config)
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
