package me.tbsten.capture.code

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall.RewriteCapturedSourceCall
import me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.RewriteCapturedSourcesCall
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn.WarnIfDuplicateMarkerFqn
import me.tbsten.capture.code.feature.markerDefinition.ir.warnIfNonCapturableMarkerUse.WarnIfNonCapturableMarkerUse
import me.tbsten.capture.code.feature.markerDefinition.ir.warnIfParameterUnused.WarnIfParameterUnused
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Capture Code compiler plugin の IR 拡張点。
 *
 * task-120-B Phase 5: IR phase の orchestration を **main 側 logic chain** に切替済。
 * これまで `CompatContext.transformIr(...)` SPI 経由で compat-kXXX に丸投げしていたが、
 * Phase 3a/4a で main 側に concrete 化した [CollectDeclarationSite] +
 * [RewriteCapturedSourcesCall] (= [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.BuildMarkerInstance]
 * + filler/userargs chain を含む) を K2.0 baseline 1 箇所で駆動する。 IR drift は
 * [me.tbsten.capture.code.compat.CompatContext] の Phase 2 で追加した 11 IR primitive
 * (`walkIrFileDeclarations` / `transformCallsInModule` / `newIrCall` / `newIrConstructorCall` /
 * `putCallValueArgument` / `getCallValueArgument` / `setCallTypeArgument` / `getCallTypeArgument`
 * / `valueParametersOf` / `deepCopyExpression` / `walkIrTree`) と `loadFileText` 経由で吸収する。
 *
 * Logic A の動的検出結果は [CaptureCodeMarkerRegistry] (FIR phase の checker が蓄積) を
 * 通じて受け渡されるため、本 extension では FIR session に直接アクセスする必要はない。
 *
 * ## Registry lifecycle
 *
 * [CaptureCodeMarkerRegistry] / [CaptureCodeExpressionSiteRegistry] は plugin module の
 * object として表現された compilation-scoped holder。 同一 JVM (= 同一 ClassLoader) で複数
 * コンパイル (例: kctfork による連続 test compile) が走る場合、 前回の marker FqN / expression
 * site が次回に漏れないよう、本 extension の `generate` 完了時に [CaptureCodeMarkerRegistry.reset]
 * / [CaptureCodeExpressionSiteRegistry.reset] (try/finally) でクリアする。
 *
 * KMP の複数 target (例: jvmMain + jsMain) のハンドリングは将来の拡張ポイント。
 *
 * ## Plugin config
 *
 * Logic I で `CompilerConfiguration` から [CaptureCodePluginConfig] を受け取り保持する。
 * Logic D (dedent) / file annotation / line info はこの config を読んで振る舞いを切り替える。
 */
public class CaptureCodeIrExtension(
    private val config: CaptureCodePluginConfig = CaptureCodePluginConfig.DEFAULT,
) : IrGenerationExtension {
    private val collectDeclarationSite = CollectDeclarationSite()
    private val rewriteCapturedSourcesCall = RewriteCapturedSourcesCall()
    private val rewriteCapturedSourceCall = RewriteCapturedSourceCall()
    private val warnIfDuplicateMarkerFqn = WarnIfDuplicateMarkerFqn()
    private val warnIfNonCapturableMarkerUse = WarnIfNonCapturableMarkerUse()
    private val warnIfParameterUnused = WarnIfParameterUnused()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        try {
            // Metro pattern: IR primitive は CompatContext の 11 SPI method 経由でバージョン差を
            // 吸収する。 CaptureCodeCompatHolder.context は process-scoped lazy で
            // ServiceLoader を 1 回だけ走らせ、 K2 compiler 起動の hot path で全 generate()
            // invocation 共有する。
            val compat = CaptureCodeCompatHolder.context
            // task-127: 経路 0a。 FIR phase で registry に積まれた registration 履歴を走査し、
            // 同 FQN を複数回 register された marker に対して `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`
            // warning を発火する。 これは CollectDeclarationSite よりも前に呼ぶ (= 後段の
            // marker FqN 解決が ambiguous な状態でもログに残るようにする)。
            warnIfDuplicateMarkerFqn(CaptureCodeMessageCollectorHolder.get())
            // bug-008 (3): 経路 0b。 登録済 marker が capture 対象外の位置 (property accessor
            // = `@get:` / `@set:` use-site target、 enum entry) に付いている場合に
            // `CC_MARKER_ON_NON_CAPTURABLE_TARGET` warning を発火する。 marker registry
            // (FIR phase 確定済) にのみ依存するので CollectDeclarationSite より前で走らせる。
            warnIfNonCapturableMarkerUse(
                moduleFragment,
                compat,
                CaptureCodeMessageCollectorHolder.get(),
            )
            // 経路 1: Logic B-ir。 declaration + file annotation + expression site を全 file 走査して
            // CollectedSite として収集する。 K{XXX}CapturedSourcesCollector の置換 (Phase 3a)。
            val collectedSites = collectDeclarationSite(moduleFragment, pluginContext, compat, config)
            // 経路 2: Logic H。 `capturedSources<T>()` の各 call を `listOf(T(...))` に書き換える。
            // K{XXX}CapturedSourcesRewriter + K{XXX}IrTransform の transformer 部分の置換 (Phase 4a)。
            // task-120-B Phase 7: opt-in flag が true なら zero-site 検出時に
            // `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warning を発火する。 そのための
            // MessageCollector は registrar 段階で CaptureCodeMessageCollectorHolder に publish 済。
            rewriteCapturedSourcesCall(
                moduleFragment,
                pluginContext,
                compat,
                config,
                collectedSites,
                CaptureCodeMessageCollectorHolder.get(),
            )
            // 経路 2': 単数版 `capturedSource<T>()` の rewrite。 複数版とは独立 logic で、
            // 件数 = 1 のみ書き換え、 0 件 / 複数件は IR phase で `MessageCollector.report(ERROR, ...)`
            // を発火する (= compile error)。 複数版とは CallableId が異なるため干渉しない。
            rewriteCapturedSourceCall(
                moduleFragment,
                pluginContext,
                compat,
                config,
                collectedSites,
                CaptureCodeMessageCollectorHolder.get(),
            )
            // 経路 3: task-128 で concrete 化した `CC_MARKER_PARAMETER_UNUSED` warning。
            // 各 marker class の constructor parameter のうち default 値あり / filler 型以外 /
            // 全 site で 1 度も override されないものについて警告する。 site 集合 (= 経路 1 の
            // 戻り値) が確定したあとに走らせる必要があるので、 rewrite chain の **後ろ** に置く。
            warnIfParameterUnused(
                collectedSites,
                pluginContext,
                compat,
                CaptureCodeMessageCollectorHolder.get(),
            )
        } finally {
            // 同一 ClassLoader での連続 compile (kctfork) で前回コンパイルの marker / expression site が
            // 次回に漏れないよう、 marker registry と expression site registry の両方をクリアする。
            CaptureCodeMarkerRegistry.reset()
            CaptureCodeExpressionSiteRegistry.reset()
        }
    }
}
