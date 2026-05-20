package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import me.tbsten.capture.code.feature.markerDefinition.MarkerDefinitionWarnings
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.text.MessageFormat

/**
 * Marker-definition warning: emits `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`
 * when two or more marker classes sharing the same FQN are registered into the
 * compilation-scoped [CaptureCodeMarkerRegistry].
 *
 * ## task-127: 具体実装
 *
 * task-120-B Phase 7 で deferred とされていた duplicate FQN 検出を本クラスで
 * concrete 化する。 検出 + warning 発火の責務分担は以下:
 *
 * - **registration 履歴**: [CaptureCodeMarkerRegistry.registrations] が各
 *   `registerMarker` 呼び出しを `MarkerRegistration` として保持する。 同 FQN を
 *   異なる declaration site から複数回 register したケースは複数 entry として
 *   積まれるので、 `duplicateMarkerFqns()` で 2 件以上のものを抽出できる。
 * - **warning 発火**: [WarnIfNoMarkerFound] と同様に IR phase の
 *   [MessageCollector] を経由して `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN`
 *   を出力する。 文面は [MarkerDefinitionWarnings.DUPLICATE_MARKER_FQN] (SSoT) を
 *   `{0}` placeholder に offending FQN を埋めて出力する。
 *
 * ## Why MessageCollector instead of DiagnosticReporter
 *
 * - FIR phase の `DiagnosticReporter` は `KtSourceElement` 必須だが IR phase には
 *   それが自然に存在しない (registrations は FIR phase で push されているが、
 *   `WarnIfDuplicateMarkerFqn` が起動する時点ではすでに source 情報は失われている)。
 * - [MessageCollector.report] は K2.0 .. K2.4-RC で同一 bytecode 互換のため
 *   `compat` SPI を介さずに直接呼べる。 [WarnIfNoMarkerFound] と同じ pattern。
 *
 * ## 発火タイミング
 *
 * [me.tbsten.capture.code.CaptureCodeIrExtension.generate] の冒頭、
 * `CollectDeclarationSite` よりも前 (= FIR phase 完了直後) に呼ぶ。 registry の
 * reset は同 extension の `finally` 節で行われるため、 本クラス起動時点では
 * registry が当該 compilation 由来の entry を保持している。
 *
 * ## False positive
 *
 * cross-module の duplicate (= 別 compilation 由来の同名 marker) は当該 compilation
 * の registry には現れない (compilation-scoped) ため検出対象外。 task-127 の範囲は
 * 同 compilation 内 (例: commonMain + jvmMain で `expect`/`actual` ではなく
 * 平行 declaration を作ってしまったケース、 または同じ package に同名 marker class
 * を 2 度宣言してしまったケース) に限定する。
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate] の冒頭、
 * `CollectDeclarationSite` 起動前) は以下を保証する責務がある。 違反時は warning が発火しない
 * だけで compile flow に影響を与えない設計のため、 `require(...)` での fail-fast は導入していない。
 *
 * - [CaptureCodeMarkerRegistry] は FIR phase 完了後の状態 (= 当該 compilation 由来の全
 *   `MarkerRegistration` が push 済)。 typical root cause: caller が FIR phase 完了前に invoke
 *   した (= phase 順序 bug)。
 * - [CaptureCodeMarkerRegistry.duplicateMarkerFqns] は 2 回以上 register された fqn のみ返す
 *   (= 内部 logic)。 重複が無い場合は早期 return で no-op。
 * - `messageCollector: MessageCollector` は IR phase collector。 [MessageCollector.NONE] を
 *   渡せば silent。 typical root cause: holder の `compute()` が呼ばれる前に invoke された (=
 *   phase 順序 bug) — silent path で degrade。
 * - [CaptureCodeMarkerRegistry.registrationsFor] が `MarkerRegistration` の `sourceFilePath`
 *   (nullable) を返す。 null の場合は warning location なしで報告 (= marker FqN で対象を特定)。
 */
public class WarnIfDuplicateMarkerFqn {

    /**
     * registry を走査し、 同 FQN が 2 件以上 register されているものに対して
     * `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` warning を発火する。
     *
     * 1 つの duplicate FQN に対して warning は **1 度だけ** 出る (= 同 FQN が 3 回
     * register されていても warning は 1 件)。 文面の `{0}` placeholder に offending
     * FQN が埋め込まれるので、 caller は marker を一意に特定できる。
     *
     * @param messageCollector IR phase の [MessageCollector]。
     *   [MessageCollector.NONE] を渡せば silent (= 既存 unit test との互換)。
     */
    public operator fun invoke(messageCollector: MessageCollector) {
        val duplicates = CaptureCodeMarkerRegistry.duplicateMarkerFqns()
        if (duplicates.isEmpty()) return

        for (fqn in duplicates) {
            val text = MessageFormat.format(
                MarkerDefinitionWarnings.DUPLICATE_MARKER_FQN.message,
                fqn,
            )
            // duplicate の最初の registration の file path を warning location として使う。
            // IR phase 起動時点で psi の source element は持っていないので、 file path
            // ベースで「どの marker が duplicate か」 だけ示し、 line/column は提供しない
            // (= location.create(path, -1, -1, null) で line/column 未指定にする)。
            // 取得不能な場合は null location で出力する。
            val firstRegistration = CaptureCodeMarkerRegistry.registrationsFor(fqn).firstOrNull()
            val location = firstRegistration?.sourceFilePath
                ?.let { CompilerMessageLocation.create(it, -1, -1, null) }
            messageCollector.report(CompilerMessageSeverity.WARNING, text, location)
        }
    }
}
