package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance

/**
 * Logic H sub-step: 各 marker site に対して `Marker(...)` インスタンスの IR を構築する。
 *
 * task-120 (IR logic 移行) で directory structure を main module に集約した版。
 *
 * ## 責務
 *
 * marker constructor の各 parameter について:
 * - **filler 型** (`Source` / `SourceLocation` / `CaptureKind`) → 対応する [filler.BuildFiller] で値を生成
 * - **ユーザ定義** parameter (primitive / KClass / enum 等) → [userargs.BuildUserArg] で
 *   call site の式 (deepCopy) または default 値を取得
 * - **EXPRESSION 起源** の場合 → [userargs.BuildUserArgPrimitive] で FIR から push された
 *   primitive 値を IR const 化
 *
 * ## drift とのトレードオフ
 *
 * `IrConstructorCallImpl.fromSymbolOwner(...)` / `putValueArgument(i, expr)` 等は K2.4-RC で
 * 削除されているため、 IR 構築本体は **compat-kXXX 側に残す**。 本 class は将来 IR 構築 drift を
 * CompatContext 経由で吸収した段階で marker constructor walk の orchestrator になる。
 */
public class BuildMarkerInstance {

    /**
     * Reserved entry point。 現状は **fail-fast placeholder**。 将来 IR 構築 drift 吸収後に
     * orchestrator となる。 task-120-B で concrete impl を埋める。
     */
    public operator fun invoke(): Nothing =
        throw UnsupportedOperationException(
            "Not yet implemented; will be filled in task-120-B. See KDoc.",
        )
}
