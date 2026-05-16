package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

/**
 * marker annotation の filler 以外のパラメータ (= ユーザが call site で指定する parameter) を
 * IR 化するための helper class。
 *
 * task-120 (IR logic 移行) で旧 `UserArgIrBuilder` (各 `compat-kXXX/userargs/`) を rename し、
 * directory structure を main module 側にミラーした版。
 *
 * ## 責務分担
 *
 * IR access API (`IrConstructorCall.getValueArgument(i)`, `IrExpression.deepCopyWithSymbols()`,
 * `IrValueParameter.defaultValue?.expression`) は K2.4-RC で削除されているため、 K2.0 baseline で
 * 書いた main bytecode から呼ぶと NoSuchMethodError になる。 そのため **IR access 本体は引き続き
 * compat-kXXX 側に残す** (`compat-k{200,202,210,220,230}/userargs/UserArgIrBuilder.kt` および
 * `compat-k240rc/userargs/UserArgIrBuilder.kt`).
 *
 * 本 class は task-124+ で IR access drift を CompatContext 経由で吸収できた時点で
 * concrete impl を担う予定。 現状は **directory structure と naming convention の集約** のみを行う。
 */
internal class BuildUserArg {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 task-120-B で IR access drift
     * を CompatContext 経由で吸収して concrete impl を埋める予定。
     */
    internal operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
