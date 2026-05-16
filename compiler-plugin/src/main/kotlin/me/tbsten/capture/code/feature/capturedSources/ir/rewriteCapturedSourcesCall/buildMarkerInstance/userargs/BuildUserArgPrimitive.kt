package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

/**
 * EXPRESSION 起源 (式 annotation) の場合、 IR phase で marker `IrConstructorCall` が残らないため、
 * FIR session storage から渡された primitive 引数を IR const に再構築する helper class。
 *
 * task-120 (IR logic 移行) で旧 `UserArgPrimitiveIrBuilder` (各 `compat-kXXX/userargs/`) を
 * rename し、 directory structure を main module 側にミラーした版。
 *
 * ## 責務分担
 *
 * IR const factory (`IrConstImpl.string`, `IrConstImpl.int`, `IrGetEnumValueImpl(...)`, etc.) は
 * K2.0 / K2.1 で signature drift があり (IrConstKind の型パラ削除等)、 K2.4-RC では追加の
 * drift がある可能性。 そのため **IR const 構築本体は引き続き compat-kXXX 側に残す**
 * (`compat-kXXX/userargs/UserArgPrimitiveIrBuilder.kt`).
 *
 * 本 class は task-124+ で IR const drift を CompatContext 経由で吸収できた時点で
 * concrete impl を担う予定。 現状は **directory structure と naming convention の集約** のみを行う。
 */
internal class BuildUserArgPrimitive {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 task-120-B で IR const drift
     * を CompatContext 経由で吸収して concrete impl を埋める予定。
     */
    internal operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
