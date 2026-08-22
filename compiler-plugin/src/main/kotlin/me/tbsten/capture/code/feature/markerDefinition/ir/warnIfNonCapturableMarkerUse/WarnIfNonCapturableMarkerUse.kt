package me.tbsten.capture.code.feature.markerDefinition.ir.warnIfNonCapturableMarkerUse

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.classFqName
import java.text.MessageFormat

/**
 * bug-008 (3): 登録済 marker が **capture 対象外の位置** に付いている場合に
 * `CC_MARKER_ON_NON_CAPTURABLE_TARGET` warning を発火する。
 *
 * ## 対象位置
 *
 * - **property accessor** (= `@get:Marker` / `@set:Marker` の use-site target)。
 *   use-site target 付き annotation は IR では accessor 関数側に付き、 declaration collector
 *   (`CollectDeclarationSite`) は accessor を意図的に skip する
 *   (`correspondingPropertySymbol != null` → property 経由で capture する方針) ため、
 *   silent に 0 件になる。
 * - **enum entry** (`@Marker RED,`)。 declaration walk の callback (class / function /
 *   property / typealias) に enum entry は含まれないため、 これも silent に 0 件になる。
 *
 * いずれも「付けたのに何も起きない」 という最悪のデバッグ体験になるので、 warning で
 * 通知する。 検出は marker annotation の存在だけで判定でき false positive が無い
 * (同一 compilation 内で marker が registry に登録済であることが前提) ため、 opt-in
 * config gate は設けない。
 *
 * ## 検出できない位置 (既知の制約)
 *
 * `@field:Marker` (backing field) / `@param:Marker` (constructor parameter) / local
 * variable への marker は、 declaration walk の callback に現れないため検出できない
 * (docs の known-limitations 側でカバーする)。
 *
 * ## Why MessageCollector instead of DiagnosticReporter
 *
 * [me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn.WarnIfDuplicateMarkerFqn]
 * と同じ理由: IR phase に `DiagnosticReporter` + `KtSourceElement` は無く、
 * `MessageCollector.report(severity, message, location)` は K2.0 .. K2.4 で bytecode
 * 互換のため compat SPI を介さず直接呼べる。 location は
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.warnIfNoMarkerFound.WarnIfNoMarkerFound]
 * と同じく `IrFile.fileEntry` の offset → line/column 変換で組み立てる。
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.CaptureCodeIrExtension.generate]) は以下を保証する
 * 責務がある。 違反時は warning が発火しないだけで compile flow に影響しない設計のため、
 * `require(...)` での fail-fast は導入していない。
 *
 * - [CaptureCodeMarkerRegistry] は FIR phase 完了後の状態 (= 当該 compilation の marker FqN
 *   が登録済)。 未登録なら早期 return で no-op。
 * - `compat.walkIrFileDeclarations` は nested declaration を再帰的に visit し、 property
 *   accessor も `onSimpleFunction` に dispatch する (K200CallbackVisitor 実装準拠)。
 * - `messageCollector` は IR phase collector。 [MessageCollector.NONE] なら silent。
 */
public class WarnIfNonCapturableMarkerUse {

    public operator fun invoke(
        moduleFragment: IrModuleFragment,
        compat: CompatContext,
        messageCollector: MessageCollector,
    ) {
        if (CaptureCodeMarkerRegistry.markerFqns.isEmpty()) return

        for (file in moduleFragment.files) {
            compat.walkIrFileDeclarations(
                file = file,
                onClass = { irClass ->
                    // enum entry は walk の callback に無いため、 class の declarations から直接拾う。
                    for (declaration in irClass.declarations) {
                        val enumEntry = declaration as? IrEnumEntry ?: continue
                        warnMarkerAnnotations(
                            annotations = enumEntry.annotations,
                            positionDescription = "an enum entry",
                            file = file,
                            startOffset = enumEntry.startOffset,
                            messageCollector = messageCollector,
                        )
                    }
                },
                onSimpleFunction = { function ->
                    // property accessor (= use-site target `@get:` / `@set:` の行き先)。
                    // 通常の関数は CollectDeclarationSite が capture するので対象外。
                    if (function.correspondingPropertySymbol != null) {
                        warnMarkerAnnotations(
                            annotations = function.annotations,
                            positionDescription =
                                "a property accessor (a use-site target such as @get: / @set:)",
                            file = file,
                            startOffset = function.startOffset,
                            messageCollector = messageCollector,
                        )
                    }
                },
            )
        }
    }

    /**
     * [annotations] のうち登録済 marker のものそれぞれについて warning を 1 件発火する。
     *
     * marker 判定は `annotation.type.classFqName` + [CaptureCodeMarkerRegistry.isMarker]
     * (= `CollectDeclarationSite` 側の filter と同じ判定)。 未解決 type は silent skip。
     */
    private fun warnMarkerAnnotations(
        annotations: List<IrConstructorCall>,
        positionDescription: String,
        file: IrFile,
        startOffset: Int,
        messageCollector: MessageCollector,
    ) {
        for (annotation in annotations) {
            val fqn = annotation.type.classFqName?.asString() ?: continue
            if (!CaptureCodeMarkerRegistry.isMarker(fqn)) continue

            val text = MessageFormat.format(
                NonCapturableMarkerUseWarnings.MARKER_ON_NON_CAPTURABLE_TARGET.message,
                fqn,
                positionDescription,
            )
            val path = file.fileEntry.name
            val line = runCatching { file.fileEntry.getLineNumber(startOffset) + 1 }.getOrDefault(-1)
            val column = runCatching { file.fileEntry.getColumnNumber(startOffset) + 1 }.getOrDefault(-1)
            val location = CompilerMessageLocation.create(path, line, column, null)
            messageCollector.report(CompilerMessageSeverity.WARNING, text, location)
        }
    }
}
