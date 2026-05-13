package me.tbsten.capture.code.compat.k2000

import com.google.auto.service.AutoService
import me.tbsten.capture.code.compat.IrInjector
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

/**
 * Kotlin 2.0.0+ 向けの IR 変換実装の scaffold。
 *
 * 本 scaffold では [K2000CapturedSourcesTransformer] を走らせるが、現状は何も書き換えない。
 * 後続 ticket (Phase 1 vertical slice) で hardcoded marker 認識・`capturedSources<T>()` の
 * 書き換えロジックを transformer 内に実装する。
 */
public class K2000IrInjector : IrInjector {
    override fun transform(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val transformer = K2000CapturedSourcesTransformer(pluginContext)
        moduleFragment.transformChildrenVoid(transformer)
    }

    @AutoService(IrInjector.Factory::class)
    public class Factory : IrInjector.Factory {
        override val minVersion: String = "2.0.0"
        override fun create(): IrInjector = K2000IrInjector()
    }
}

/**
 * IR を走査して `@CaptureCode` 由来のキャプチャを構築する transformer の scaffold。
 *
 * Phase 1 の本 ticket では何も書き換えない空 visitor として置く。
 * 後続 ticket でこの transformer に以下のロジックを実装する想定:
 *
 * - hardcoded marker (`com.example.Snippets`) を付けた declaration を検出して source を収集 (task-005)
 * - `capturedSources<Snippets>()` の `IrCall` を `listOf(Snippets(source = Source(...)))` に置換 (task-006)
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic B/C/D/H、§6 Phase ordering 参照。
 */
internal class K2000CapturedSourcesTransformer(
    @Suppress("unused") private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid()
