package me.tbsten.capture.code.feature.capturedSources.fir.collectRunWithCaptureCodeSite

import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeCallableIds
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMetaAnnotation
import me.tbsten.capture.code.utils.fir.compilerMessageLocationOf
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import java.text.MessageFormat

/**
 * Logic B-block: `runWithCaptureCode(Marker::class) { ... }` の site 収集。
 *
 * expression annotation (`@Marker() (expr)`) と同じ FIR → IR 受け渡し経路
 * ([CaptureCodeExpressionSiteRegistry]) を使うが、 site の意味が 1 点だけ異なる:
 *
 * - expression annotation: `startOffset..endOffset` が **そのまま** capture 対象
 * - `runWithCaptureCode`: `startOffset..endOffset` は **呼び出し式全体** を指し、
 *   IR phase で最外殻の `{` `}` を落として lambda body だけを取り出す
 *   (= [CaptureCodeExpressionSiteRegistry.Site.unwrapBlockBody] `= true`)
 *
 * 呼び出し全体の range をそのまま push するのは意図的な設計。 FIR の lambda 引数の AST 形
 * (`FirLambdaArgumentExpression` / `FirAnonymousFunctionExpression` の入れ子) は Kotlin
 * minor version 間で drift するため、 lambda を AST から辿ると compat SPI method が増える。
 * 「呼び出し式全体の source range」 は `FirElement.source` から drift なしに取れるので、
 * brace の除去は file text を持っている IR phase 側に寄せている。
 *
 * ## Preconditions
 *
 * Caller (= 各 `compat-kXXX` の `K{XXX}ExpressionSiteCollector`) は以下を保証する責務がある。
 * 違反時は silent skip (+ 主要経路は verbose-log) で、 `require(...)` での fail-fast は
 * **導入しない**。 FIR checker は resolution error を含む file に対しても走るため、
 * `require` は user の typo 1 つで compiler を INTERNAL_ERROR に落としてしまう。
 *
 * - `expression: FirStatement` は FIR-resolved な statement。 未解決 callee
 *   (`FirErrorNamedReference`) の場合は安全に early-return する。
 * - `expression.source` が non-null かつ `0 <= startOffset < endOffset`。
 *   違反時は verbose-log 付きで skip。
 * - marker 引数 (`Marker::class`) が `FirGetClassCall` として解決済。 解決できない場合は skip
 *   (= IR phase の [me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry]
 *   filter でも弾かれるため二重に安全)。
 *
 * ## bug-008 (2): 非 marker class は error
 *
 * marker 引数の class が解決できて **`@CaptureCode` meta を持たない** 場合、 旧実装は site を
 * push するだけで IR phase の registry filter に silent に弾かれていた (= capture 0 件の
 * silent no-op)。 bug-008 で [RunWithCaptureCodeCallErrors.MARKER_NOT_CAPTURE_CODE] を
 * [CaptureCodeMessageCollectorHolder.reportError] (MessageCollector ERROR = compile 失敗)
 * で報告するように変更。 `KtDiagnosticFactory*` を使わないのは、 新 factory の追加が全
 * compat-kXXX の diagnostics object に波及するため (詳細は
 * [RunWithCaptureCodeCallErrors] の KDoc)。 class symbol 自体が解決できない場合は従来通り
 * silent skip (別の compiler error が主因のはずで、 二重報告を避ける)。
 */
public class CollectRunWithCaptureCodeSite {

    public operator fun invoke(
        context: CheckerContext,
        @Suppress("UNUSED_PARAMETER") reporter: DiagnosticReporter,
        expression: FirStatement,
        compat: CompatContext,
    ) {
        if (expression !is FirFunctionCall) return
        if (!expression.isRunWithCaptureCodeCall()) return

        val markerType = expression.markerConeTypeOrNull(compat) ?: run {
            CaptureCodeMessageCollectorHolder.reportLogging(
                "[CaptureCode] runWithCaptureCode(...) call has no resolvable marker class " +
                    "argument; skipping block site.",
            )
            return
        }
        val markerFqn = compat.classIdOfType(markerType)?.asSingleFqName()?.asString() ?: run {
            CaptureCodeMessageCollectorHolder.reportLogging(
                "[CaptureCode] runWithCaptureCode(...) call has no resolvable marker class " +
                    "argument; skipping block site.",
            )
            return
        }

        // bug-008 (2): marker class が解決できて @CaptureCode meta を持たない場合は error。
        // silent 0 件 capture ではなく compile error として誤用を通知する。 symbol 未解決
        // (= 別の compiler error が主因) は従来通り silent skip。
        val markerClassSymbol = compat.toRegularClassSymbolOrNull(markerType, context.session)
        if (markerClassSymbol != null && !markerClassSymbol.hasCaptureCodeMeta(context.session)) {
            CaptureCodeMessageCollectorHolder.reportError(
                message = MessageFormat.format(
                    RunWithCaptureCodeCallErrors.MARKER_NOT_CAPTURE_CODE.message,
                    markerFqn,
                ),
                location = compilerMessageLocationOf(
                    source = expression.source,
                    fallbackFilePath = compat.containingFilePathOf(context),
                ),
            )
            return
        }

        val source = expression.source ?: run {
            CaptureCodeMessageCollectorHolder.reportLogging(
                "[CaptureCode] runWithCaptureCode(...) call for marker '$markerFqn' has no source " +
                    "element (synthetic call); skipping block site.",
            )
            return
        }

        val startOffset = source.startOffset
        val endOffset = source.endOffset
        if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) {
            CaptureCodeMessageCollectorHolder.reportLogging(
                "[CaptureCode] runWithCaptureCode(...) call for marker '$markerFqn' has invalid " +
                    "source offsets ($startOffset..$endOffset); skipping block site.",
            )
            return
        }

        val filePath = resolveFilePath(source, compat.containingFilePathOf(context), markerFqn)
            ?: return

        val site = CaptureCodeExpressionSiteRegistry.Site(
            filePath = filePath,
            startOffset = startOffset,
            endOffset = endOffset,
            markerFqn = markerFqn,
            userArgs = emptyMap(),
            unwrapBlockBody = true,
        )
        if (CaptureCodeExpressionSiteRegistry.allSites.any { it == site }) return
        CaptureCodeExpressionSiteRegistry.addSite(site)
    }

    /**
     * 当該 call が `me.tbsten.capture.code.runWithCaptureCode` かを判定する。
     *
     * 未解決 callee (`FirErrorNamedReference` 等) では `false` を返して安全に抜ける。
     */
    private fun FirFunctionCall.isRunWithCaptureCodeCall(): Boolean {
        val reference = calleeReference as? FirResolvedNamedReference ?: return false
        val symbol = reference.resolvedSymbol as? FirCallableSymbol<*> ?: return false
        return symbol.callableId == CaptureCodeCallableIds.runWithCaptureCode
    }

    /**
     * `runWithCaptureCode(Marker::class) { ... }` の第 1 引数 (`Marker::class`) から marker class の
     * [ConeKotlinType] を取り出す。
     *
     * 引数位置ではなく **`FirGetClassCall` である最初の引数** を探すので、 named argument で
     * 順序が入れ替わっていても拾える。 FqN の取り出し (`classIdOfType`) と `@CaptureCode` meta
     * 検査 (`toRegularClassSymbolOrNull`, bug-008) の両方が同じ型を起点にするため、 型のまま返す。
     */
    private fun FirFunctionCall.markerConeTypeOrNull(compat: CompatContext): ConeKotlinType? {
        val getClassCall = arguments.filterIsInstance<FirGetClassCall>().firstOrNull() ?: return null
        val classArgument = getClassCall.arguments.firstOrNull() ?: return null
        // drift D13/D14: `FirExpression.resolvedType` + `ConeKotlinType.classId` は SPI 経由で dispatch。
        return compat.resolvedTypeOrNullOf(classArgument)
    }

    /**
     * bug-008 (2): marker class symbol が `@CaptureCode` meta を持つかを判定する。
     * [me.tbsten.capture.code.feature.capturedSources.fir.validateCapturedSourcesCall.ValidateCapturedSourcesCall]
     * の同名 private extension と同じ実装 (logic ローカルに閉じるため意図的に重複)。
     */
    private fun FirRegularClassSymbol.hasCaptureCodeMeta(session: FirSession): Boolean =
        annotations.any { it.toAnnotationClassId(session) == CaptureCodeMetaAnnotation.classId }

    /**
     * site の file path を「PSI virtualFile の絶対パス → CheckerContext の file path →
     * (最後の手段) bare file name」 の順で解決する。
     *
     * bare file name は `CollectDeclarationSite.matchesFile` が完全一致でしか採用しないため、
     * そこまで落ちたことは verbose-log に残す (task-149 と同じ方針)。
     */
    private fun resolveFilePath(
        source: KtSourceElement,
        contextFilePath: String?,
        markerFqn: String,
    ): String? {
        val absolutePath = (source as? KtPsiSourceElement)?.psi?.containingFile?.virtualFile?.path
        val resolved = absolutePath ?: contextFilePath
        if (resolved != null) return resolved

        val bareFileName = (source as? KtPsiSourceElement)?.psi?.containingFile?.name
        if (bareFileName == null) {
            CaptureCodeMessageCollectorHolder.reportLogging(
                "[CaptureCode] runWithCaptureCode(...) call for marker '$markerFqn' has no " +
                    "resolvable containing file path; skipping block site.",
            )
            return null
        }
        CaptureCodeMessageCollectorHolder.reportLogging(
            "[CaptureCode] Could not resolve an absolute path for the file containing a " +
                "runWithCaptureCode(...) call for marker '$markerFqn'; falling back to the bare " +
                "file name '$bareFileName'. The block site is matched only against an exactly " +
                "equal IR file path, so this capture may be skipped.",
        )
        return bareFileName
    }
}
