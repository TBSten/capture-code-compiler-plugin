package me.tbsten.capture.code.compat.k200

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k200.checker.FullyExpandedTypeShim
import me.tbsten.capture.code.compat.k200.checker.K200CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k200.checker.K200MarkerCheckersExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
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

    override fun containingFilePathOf(context: CheckerContext): String? =
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        // task-080: 2.0.x の `fullyExpandedType(session)` overload は 2.0.20 で削除されたため、
        // reflection shim 経由で 2.0.0 baseline / 2.0.20+ 両方を吸収する。
        FullyExpandedTypeShim.expand(type, session)

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K200MarkerCheckersExtension,
        ::K200MarkerAnnotationCheckersExtension,
        ::K200CapturedSourcesCallCheckersExtension,
        ::K200ExpressionAnnotationCheckersExtension,
    )

    // task-078: FIR / IR 拡張の登録は CompilerPluginRegistrar.ExtensionStorage の
    // `registerExtension(<Descriptor>, T)` を呼ぶが、 その descriptor の super class
    // (ProjectExtensionDescriptor vs ExtensionPointDescriptor) と
    // ExtensionStorage 側の引数型が Kotlin 2.3.0 で drift している。
    // 本 module は 2.0.0 native API でビルドされるため、 ここでの call site は
    // 2.0.x の ProjectExtensionDescriptor シグネチャに正しくリンクされる。
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
        override val minVersion: String = "2.0.0"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
