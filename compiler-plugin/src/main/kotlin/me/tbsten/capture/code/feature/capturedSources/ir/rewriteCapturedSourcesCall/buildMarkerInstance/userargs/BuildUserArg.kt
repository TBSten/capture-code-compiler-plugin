package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs

import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * marker annotation の filler 以外のパラメータ (= ユーザが call site で指定する parameter) を
 * IR 化するための helper class。
 *
 * task-120-B Phase 4a で signature を確定。 invoke 本体は **Phase 4b 持ち越し** (= 現状は
 * `UnsupportedOperationException` を投げる placeholder)。 [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance]
 * が parameter 種別判定後に本 class を呼び出す chain を Phase 4a で組み立てる。
 *
 * ## 責務 (Phase 4b 以降)
 *
 * - markerCall (= declaration / file 起源で non-null) があれば `compat.getCallValueArgument` で
 *   ユーザの IR 式を取り出し、 `compat.deepCopyExpression` で deepCopy して返す
 * - markerCall が null (= EXPRESSION 起源) または argument が `null` (= 省略) なら、
 *   marker class の `IrValueParameter.defaultValue?.expression` を deepCopy して返す
 * - default 値も無い (= required parameter 省略) なら `null` を返す (= 上位は putValueArgument を
 *   skip し、 primary constructor の default で fill)
 *
 * ## 旧構造との関係
 *
 * 既存 `K{XXX}/userargs/UserArgIrBuilder.kt` (`buildOrDefault` 関数) が runtime path として残り、
 * Phase 4a 段階では本 class は caller (= [BuildMarkerInstance]) も含めて runtime に到達しない。
 * Phase 4b で `UserArgIrBuilder.buildOrDefault` のロジックを本 class invoke に移植し、 IR access
 * は CompatContext 経由に置換する。
 */
internal class BuildUserArg {

    /**
     * call site で指定された値か marker class の default 値を deepCopy して返す。
     *
     * Phase 4b で concrete impl を入れる。 signature は既存 `UserArgIrBuilder.buildOrDefault` に
     * compat 引数を追加した形 (= IR access は `compat.getCallValueArgument` / `compat.deepCopyExpression`
     * 経由で K2.4-RC 削除 API drift を吸収)。
     */
    @Suppress("UNUSED_PARAMETER")
    internal operator fun invoke(
        markerCall: IrConstructorCall?,
        parameterIndex: Int,
        parameter: IrValueParameter,
        compat: CompatContext,
    ): IrExpression? =
        throw UnsupportedOperationException(
            "BuildUserArg.invoke is a Phase 4a signature placeholder. " +
                "Concrete impl arrives in task-120-B Phase 4b.",
        )
}
