package me.tbsten.capture.code.compat.k230

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k230.checker.K230CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k230.checker.K230MarkerCheckersExtension
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
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin 2.3.x 向けの [CompatContext] 実装。
 *
 * task-075: compat-k220 (Kotlin 2.2.x baseline) を template に mechanical copy + rename
 * したもの。 2.2.x → 2.3.x の追加 native API drift は本 module で吸収する。 現時点で
 * 確認している方針:
 *
 * - **FIR checker `check()` signature**: 2.2.x で導入された `context(...) fun check(D)`
 *   (Java shim 経由) は 2.3.x でもそのまま有効と想定。
 * - **IR API drift**: `IrMemberAccessExpression.putValueArgument` 等の deprecated migration
 *   は 2.3.x でも継承。 大きな drift が観測されたら個別 ticket で対応。
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
        runK230IrTransform(moduleFragment, pluginContext, config)
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.3.x: FirLiteralExpression は型パラメータ無し (2.0.21+ と同形)。
        return (expression as? FirLiteralExpression)?.value
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean =
        expression is FirLiteralExpression

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? = type.toRegularClassSymbol(session)

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? = symbol.classId

    override fun containingFilePathOf(context: CheckerContext): String? =
        // Kotlin 2.3.x: `CheckerContext.containingFile` accessor が削除され、
        // `containingFilePath: String?` に置き換わった (drift D12)。
        context.containingFilePath

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        type.fullyExpandedType(session)

    override fun loadFileText(file: IrFile): String? = SourceTextExtractor.loadFileText(file)

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K230MarkerCheckersExtension,
        ::K230MarkerAnnotationCheckersExtension,
        ::K230CapturedSourcesCallCheckersExtension,
        ::K230ExpressionAnnotationCheckersExtension,
    )

    // task-078: 詳細は CompatContext.registerExtensions の KDoc 参照。
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
        override val minVersion: String = "2.3.0"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
