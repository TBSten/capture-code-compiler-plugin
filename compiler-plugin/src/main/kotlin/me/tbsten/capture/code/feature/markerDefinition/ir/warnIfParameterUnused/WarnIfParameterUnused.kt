package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfParameterUnused

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeFillerClassIds
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.MarkerDefinitionWarnings
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.text.MessageFormat

/**
 * Marker-definition warning: emits `CC_MARKER_PARAMETER_UNUSED` for each marker
 * constructor parameter that **carries a default value** but is **never
 * overridden** at any captured site in the current compilation.
 *
 * ## task-128: 具体実装
 *
 * task-120-B Phase 7 で deferred とされていた unused parameter 検出を本クラスで
 * concrete 化する。 検出 + warning 発火の責務分担は以下:
 *
 * - **marker class 走査**: [CaptureCodeMarkerRegistry.markerFqns] に登録された各 FQN を
 *   [IrPluginContext.referenceClass] で IR class symbol に解決し、 primary constructor の
 *   value parameter を [CompatContext.valueParametersOf] で取得する。
 * - **filler 除外**: parameter の型が `Source` / `SourceLocation` / `CaptureKind` の場合は
 *   plugin 側で自動 fill されるため unused 判定対象外。
 * - **default 値判定**: `parameter.defaultValue == null` の parameter は unused 判定対象外
 *   (= 必須 parameter は site 側で必ず override されるはず)。
 * - **site 走査**: 当該 marker の全 site (`allCollectedSites.filter { it.site.markerFqn == fqn }`)
 *   を回し、 各 parameter が override されているかを確認:
 *   - **declaration / file 起源** (`markerCall != null`): `compat.getCallValueArgument(markerCall, index)`
 *     が non-null なら override されている
 *   - **EXPRESSION 起源** (`markerCall == null`): `expressionUserArgs[paramName]` に entry が
 *     あれば override されている
 * - **warning 発火**: site が 0 件 (= 当該 marker が一切使われていない) でも default 値 parameter
 *   は意味を持たないため発火する。 同 `(markerFqn, paramName)` ペアに対する warning は **1 度だけ**
 *   出力する (= site 数によらず常に 1 message)。
 *
 * ## Why MessageCollector instead of DiagnosticReporter
 *
 * [WarnIfNoMarkerFound] / [WarnIfDuplicateMarkerFqn] と同じ理由。 IR phase では
 * `KtSourceElement` が手軽に手に入らず、 K2.0 .. K2.4-RC で `MessageCollector.report` の
 * bytecode 互換が確認済なので main module から直接呼ぶ。
 *
 * ## 発火タイミング
 *
 * [me.tbsten.capture.code.CaptureCodeIrExtension.generate] の末尾、
 * `RewriteCapturedSourcesCall` (= 全 site の確定後) のあとに呼ぶ。 registry の reset は
 * 同 extension の `finally` 節で行われるため、 本クラス起動時点では registry が当該
 * compilation 由来の entry を保持している。
 *
 * ## 動作保証スコープ (task-128 Phase 1)
 *
 * - 保証する: declaration / file annotation 起源と EXPRESSION 起源の両方で override 検出
 * - 保証する: 同 `(markerFqn, paramName)` ペアの warning は 1 度だけ発火
 * - スコープ外: cross-compilation の override 検出 (registry は compilation-scoped のため)
 * - スコープ外: marker class が IR phase で resolve 不能 (= runtime 依存不足) の場合は silent skip
 *   (= 既存 `BuildMarkerInstance` が `CC_CAPTUREDSOURCES_REWRITE_FAILED` で別途警告するため
 *   ここで重ねて出さない)
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate] の末尾、
 * `RewriteCapturedSourcesCall` 起動後) は以下を保証する責務がある。 違反時は warning が発火し
 * ないだけで compile flow に影響を与えない設計のため、 `require(...)` での fail-fast は導入して
 * いない。
 *
 * - `allCollectedSites: List<CollectedSite>` は [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectDeclarationSite]
 *   の戻り値 (= 各 site が `CollectedSite` の不変条件を満たす)。 typical root cause: caller が
 *   hand-built site を渡している (= unit test 引数 typo)。
 * - `pluginContext: IrPluginContext` は IR phase で resolved。 marker class symbol が
 *   `referenceClass` で取得可能。 取得不能の場合は silent skip (= 既存 BuildMarkerInstance で
 *   `CC_CAPTUREDSOURCES_REWRITE_FAILED` 別途警告するため重ね報告しない)。
 * - `compat: CompatContext` は `valueParametersOf` / `getCallValueArgument` の SPI が正しく
 *   dispatch される (= K2.4-RC drift を吸収)。
 * - `messageCollector: MessageCollector` は IR phase collector。 [MessageCollector.NONE] を
 *   渡せば silent (= 既存 unit test 互換)。
 * - [CaptureCodeMarkerRegistry][me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   は FIR phase 完了後の状態 (= 当該 compilation 由来の全 marker fqn が登録済)。 空 registry
 *   の場合は早期 return で no-op。
 */
public class WarnIfParameterUnused {

    /**
     * registry に登録された各 marker class の primary constructor を走査し、 default 値あり
     * かつ全 site で override されていない parameter について `CC_MARKER_PARAMETER_UNUSED`
     * warning を発火する。
     *
     * @param allCollectedSites module 全体から収集した [CollectedSite] のリスト。
     *   `CollectDeclarationSite` の戻り値をそのまま渡せばよい。
     * @param pluginContext IR phase の [IrPluginContext]。 marker class symbol 解決に使う。
     * @param compat IR primitive (`valueParametersOf`, `getCallValueArgument`) を委譲する SPI
     * @param messageCollector IR phase の [MessageCollector]。 [MessageCollector.NONE] を
     *   渡せば silent (= 既存 unit test との互換)。
     */
    public operator fun invoke(
        allCollectedSites: List<CollectedSite>,
        pluginContext: IrPluginContext,
        compat: CompatContext,
        messageCollector: MessageCollector,
    ) {
        val markerFqns = CaptureCodeMarkerRegistry.markerFqns
        if (markerFqns.isEmpty()) return

        // filler 型 FQN を 1 回だけ計算 (per-marker loop の hot path で文字列比較する)。
        val fillerFqns = setOf(
            CaptureCodeFillerClassIds.Source.asFqNameString(),
            CaptureCodeFillerClassIds.SourceLocation.asFqNameString(),
            CaptureCodeFillerClassIds.CaptureKind.asFqNameString(),
        )

        for (markerFqn in markerFqns) {
            val markerSymbol = pluginContext.referenceClass(
                ClassId.topLevel(FqName(markerFqn)),
            ) ?: continue
            // primary constructor を取得 (annotation class は 1 つしか持てないので first で十分)。
            val constructor = markerSymbol.owner.constructors.firstOrNull { it.isPrimary }
                ?: markerSymbol.owner.constructors.firstOrNull()
                ?: continue
            val parameters = compat.valueParametersOf(constructor)
            if (parameters.isEmpty()) continue

            val sitesForMarker = allCollectedSites.filter { it.site.markerFqn == markerFqn }
            // 0-site marker は `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` が既に「marker が使われ
            // ていない」 ことを通知するため、 param level の「unused」 指摘は冗長 noise になる。
            // user は site を追加するか marker を削除するかを判断する段階で、 さらに細かい
            // parameter 指摘は意思決定を妨げる。 site が 1 つ以上ある marker に限定して warning
            // を出す。
            if (sitesForMarker.isEmpty()) continue

            parameters.forEachIndexed { index, parameter ->
                // default 値なし parameter は必須引数 (= site 側で必ず override される) なので
                // unused 判定対象外。 また filler 型 parameter は plugin 側で自動 fill されるため
                // user の override が無いのは想定通り。
                if (parameter.defaultValue == null) return@forEachIndexed
                val paramTypeFqn = parameter.type.classFqName?.asString()
                if (paramTypeFqn in fillerFqns) return@forEachIndexed

                val paramName = parameter.name.asString()
                val overriddenAtAnySite = sitesForMarker.any { site ->
                    isParameterOverridden(site, index, paramName, compat)
                }
                if (overriddenAtAnySite) return@forEachIndexed

                emitWarning(messageCollector, markerFqn, paramName)
            }
        }
    }

    /**
     * 1 site で指定 parameter が override されているかを判定する。
     *
     * - declaration / file 起源 (`markerCall != null`): 当該 index の value argument が
     *   non-null なら override されている (= site 側で `Marker(param = ...)` の形で値を指定)。
     * - EXPRESSION 起源 (`markerCall == null`): FIR phase で push された `expressionUserArgs`
     *   map に当該 parameter 名の entry があれば override されている。
     */
    private fun isParameterOverridden(
        site: CollectedSite,
        index: Int,
        paramName: String,
        compat: CompatContext,
    ): Boolean {
        val markerCall = site.markerCall
        return if (markerCall != null) {
            compat.getCallValueArgument(markerCall, index) != null
        } else {
            site.expressionUserArgs.containsKey(paramName)
        }
    }

    /**
     * `CC_MARKER_PARAMETER_UNUSED` warning を 1 件出力する。 文面は
     * [MarkerDefinitionWarnings.PARAMETER_UNUSED] (SSoT) を `{0}` placeholder に
     * `"<markerFqn>.<paramName>"` を埋めて出力する。 IR phase では psi source element が
     * 自然に取れないため location は `null` (= marker FqN + param 名で対象一意に特定)。
     */
    private fun emitWarning(
        messageCollector: MessageCollector,
        markerFqn: String,
        paramName: String,
    ) {
        val payload = "$markerFqn.$paramName"
        val text = MessageFormat.format(
            MarkerDefinitionWarnings.PARAMETER_UNUSED.message,
            payload,
        )
        messageCollector.report(CompilerMessageSeverity.WARNING, text, null)
    }
}
