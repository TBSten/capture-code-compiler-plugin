package me.tbsten.capture.code.compat.k202

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k202.checker.K202CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k202.checker.K202MarkerCheckersExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin 2.0.10 〜 2.0.21 (= 2.0.x patch level) 向けの [CompatContext] 実装。
 *
 * task-081: 2.0.20+ runtime と 2.0.0 baseline binary (compat-k200) との間で識別された
 * 多数の binary-incompat drift を、 個別 reflection shim ではなく「2.0.21 baseline で
 * compile された別 module」 として ServiceLoader 経由で dispatch することで吸収する。
 *
 * 主な drift (task-080 で 2.0.21 simulation 時に surface):
 *
 * - **FIR drift D1**: `FirLiteralExpression<T>` の型パラメータ削除。 ここでは 2.0.21 native の
 *   非ジェネリック `FirLiteralExpression` を直接 dispatch する。
 * - **IR drift**: `IrVarargImplKt` / `IrGetEnumValueImplKt` / `IrConstImplKt` の top-level
 *   factory が削除され、 class constructor のみが残った。 各 builder は class constructor を
 *   直接呼ぶ形に書き換える ([K202CapturedSourcesRewriter] /
 *   [me.tbsten.capture.code.compat.k202.userargs.UserArgPrimitiveIrBuilder] を参照)。
 * - **IR drift**: `IrCallImpl(..., valueArgumentsCount = ...)` constructor 廃止。 top-level
 *   factory function (`org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl(...)`) を経由し、
 *   `valueArgumentsCount` は symbol.owner.valueParameters.size から自動初期化される。
 * - **IR drift**: `IrConst<T>` / `IrConstKind<T>` の型パラ削除。 非ジェネリック `IrConst` を使う。
 * - **IR drift**: `DeepCopyTypeRemapper(SymbolRemapper)` ctor → `(ReferencedSymbolRemapper)`。
 *   `deepCopyWithSymbols()` extension は 2.0.21 native でそのまま呼べる (内部実装変更は吸収済)。
 *
 * `toRegularClassSymbol` extension は 2.0.21 では引き続き `org.jetbrains.kotlin.fir.types`
 * package に存在する (2.1.0 で `fir.resolve` に移動)。
 *
 * Service 登録: [Factory] が `@AutoService(CompatContext.Factory::class)` で
 * `META-INF/services/me.tbsten.capture.code.compat.CompatContext$Factory` に登録される。
 * `minVersion = "2.0.10"` を宣言し、 resolveFactory が 2.0.10 / 2.0.20 / 2.0.21 patch で
 * 本 module を自動選択する (compat-k200 minVersion 2.0.0 より新しいため)。
 */
public class CompatContextImpl : CompatContext {

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        runK202IrTransform(moduleFragment, pluginContext, config)
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.0.21+ では FirLiteralExpression の `<T>` 型パラメータが削除されている
        // (task-080 解析 / FIR drift D1)。 2.0.21 native API では型パラ無しで dispatch する。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K202MarkerCheckersExtension,
        ::K202MarkerAnnotationCheckersExtension,
        ::K202CapturedSourcesCallCheckersExtension,
        ::K202ExpressionAnnotationCheckersExtension,
    )

    // task-078: FIR / IR 拡張の登録は CompilerPluginRegistrar.ExtensionStorage の
    // `registerExtension(<Descriptor>, T)` を呼ぶが、 その descriptor の super class
    // (ProjectExtensionDescriptor vs ExtensionPointDescriptor) と
    // ExtensionStorage 側の引数型が Kotlin 2.3.0 で drift している。
    // 本 module は 2.0.21 native API でビルドされるため、 ここでの call site は
    // 2.0.21 の ProjectExtensionDescriptor シグネチャに正しくリンクされる。
    override fun registerExtensions(
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        configuration: CompilerConfiguration,
        config: CaptureCodePluginConfig,
        firRegistrar: FirExtensionRegistrarAdapter,
        irExtension: IrGenerationExtension,
    ) {
        with(extensionStorage) {
            FirExtensionRegistrarAdapter.registerExtension(firRegistrar)
            IrGenerationExtension.registerExtension(irExtension)
        }
    }

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        // task-081: 2.0.20 / 2.0.21 patch を本 module が dispatch する。
        //
        // resolveFactory は `<= currentVersion` の中で minVersion が最大のものを選ぶため、
        //   - 2.0.0  → compat-k200 (minVersion 2.0.0)
        //   - 2.0.10 → compat-k200 (minVersion 2.0.10 を試したが、 2.0.21 native binary が
        //              `org.jetbrains.kotlin.ir.expressions.impl.BuildersKt` (= 2.0.20 で追加
        //              された top-level factory file) を参照するため NoClassDefFoundError。
        //              よって 2.0.10 は引き続き compat-k200 が dispatch する)
        //   - 2.0.20 → compat-k202 (minVersion 2.0.20、 BuildersKt を含む)
        //   - 2.0.21 → compat-k202
        //   - 2.1.0+ → compat-k210+ (minVersion 2.1.0 が 2.0.20 より新しい)
        //
        // 2.0.10 については task-080 で fullyExpandedType reflection shim 経由で compat-k200
        // 自体が drift-safe なため別 module を必要としない。 2.0.20+ で集中して surface した
        // IrVarargImplKt / IrConstImplKt / IrGetEnumValueImplKt 削除 + BuildersKt 導入は
        // compat-k202 で吸収する。
        override val minVersion: String = "2.0.20"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
