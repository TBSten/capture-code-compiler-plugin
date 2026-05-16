package me.tbsten.capture.code.compat.k220

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.compat.k220.checker.K220CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220MarkerAnnotationCheckersExtension
import me.tbsten.capture.code.compat.k220.checker.K220MarkerCheckersExtension
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
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin 2.2.x 向けの [CompatContext] 実装。
 *
 * 2.1.x からは下記 drift を **追加で** 吸収する (FIR D1–D4 / IR D5–D7 は 2.1.x で吸収済の
 * パターンをそのまま継承):
 *
 * - **FIR checker `check()` signature drift**: 2.0 / 2.1 系では
 *   `FirDeclarationChecker.check(declaration, context, reporter)` だったのに対し、
 *   2.2.x からは `check(context, reporter)` (declaration は `CheckerContext` 経由で取れる)
 *   に変わっている。 ただし `FirDeclarationChecker<T>` の 2.2.x 抽象 method は依然
 *   declaration を渡す形を維持しているケースがあり、 patch によって差分が出ることが
 *   ある。 本 module の checker は **共通の compat checker (k210 由来) と同じ
 *   `check(declaration, context, reporter)` 形** に揃え、 2.2.x で signature drift が
 *   観測された patch では task-072 / task-074 で扱う方針に従う。
 * - **IR drift D8**: 2.2.x では `IrMemberAccessExpression.putValueArgument(int, IrExpression)`
 *   が deprecated / 削除候補に入り、 代わりに `arguments[i] = expr` (MutableList migration)
 *   が推奨される。 ただし 2.2.0 final 時点では旧 API も残存しているため、 既存 k210 と
 *   同じ `putValueArgument(...)` 呼び出しが compile / runtime とも通る。
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
        runK220IrTransform(moduleFragment, pluginContext, config)
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        // Kotlin 2.2.x: FirLiteralExpression は型パラメータ無し (2.0.21+ と同形)。
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
        context.containingFile?.sourceFile?.path

    override fun fullyExpandedTypeOf(type: ConeKotlinType, session: FirSession): ConeKotlinType =
        type.fullyExpandedType(session)

    override fun firAdditionalCheckersExtensions():
        List<(FirSession) -> FirAdditionalCheckersExtension> = listOf(
        ::K220MarkerCheckersExtension,
        ::K220MarkerAnnotationCheckersExtension,
        ::K220CapturedSourcesCallCheckersExtension,
        ::K220ExpressionAnnotationCheckersExtension,
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
        override val minVersion: String = "2.2.0"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
