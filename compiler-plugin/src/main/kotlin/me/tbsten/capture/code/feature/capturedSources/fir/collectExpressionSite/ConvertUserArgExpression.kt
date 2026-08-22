package me.tbsten.capture.code.feature.capturedSources.fir.collectExpressionSite

import me.tbsten.capture.code.compat.CompatContext
import me.tbsten.capture.code.feature.capturedSources.UserArgValue
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

/**
 * Logic B-fir helper: expression 起源 marker の user argument 1 つ ([FirExpression]) を
 * [UserArgValue] sealed に変換する converter。 bug-004 で [CollectExpressionSite] から
 * 切り出し、 変換 branch を拡張した。
 *
 * ## 変換 branch (優先順)
 *
 * 1. literal (`42` / `"str"` / `true` 等) → [UserArgValue.wrapLiteralValue]
 * 2. `X::class` ([FirGetClassCall]) → [UserArgValue.ClassRef]
 * 3. vararg / named-argument wrapper → 中身を再帰変換
 * 4. 関数呼び出し ([FirFunctionCall]):
 *    - annotation constructor call (`Meta(note = "x")`) → [UserArgValue.AnnotationValue]
 *    - `unaryMinus` (`-42L` / `-1.5` — K2 FIR は負数 literal を receiver literal への
 *      `unaryMinus()` 呼び出しとして表現する。 負の Int だけは FIR が literal に畳み込む
 *      ため本 branch には来ない) → receiver を再帰変換して負値化
 *    - それ以外 (`BASE * 2 + 1` 等の複合定数式) → [UserArgValue.UnsupportedExpression]
 * 5. property / qualified access:
 *    - enum entry ([FirEnumEntrySymbol]) → [UserArgValue.EnumRef]
 *    - `const val` 参照 → initializer を再帰変換して畳み込み
 *    - それ以外 → [UserArgValue.UnsupportedExpression]
 * 6. 配列 literal (`["a", "b"]`) → [UserArgValue.ArrayValue]。
 *    **drift 注意**: `FirArrayLiteral` は Kotlin 2.4 で `FirCollectionLiteral` に rename
 *    されたため型名は参照せず、 「[FirCall] かつ resolved type が array 型」 で構造的に
 *    判定する (両クラスとも全 baseline で [FirCall] を implement することを javap で確認済)
 * 7. その他 → [UserArgValue.UnsupportedExpression]
 *
 * ## drift 方針
 *
 * main module は Kotlin 2.0.0 baseline で compile され runtime は 2.0 - 2.4 の任意
 * バージョンのため、 使用する FIR API は以下に限定する (bug-004 で 2.0.0 - 2.4.20-RC の
 * kotlin-compiler-embeddable を javap して bytecode signature の安定を確認済):
 *
 * - 構造的 node ([FirFunctionCall] / [FirGetClassCall] / [FirCall] /
 *   [FirQualifiedAccessExpression] / [FirResolvedArgumentList] / wrapper 2 種) の
 *   instanceof + interface method (`argumentList` / `explicitReceiver` / `mapping`)
 * - symbol 側は `toResolvedCallableSymbol()` + `callableId` safe-call (既存
 *   [CollectExpressionSite] と同 pattern) と `FirCallableSymbol.resolvedStatus` /
 *   `FirVariableSymbol.resolvedInitializer` (どちらも symbol 側の public member で
 *   `@SymbolInternals` 不要)
 * - literal 値の取り出しは [CompatContext.literalValueOrNull] SPI (drift D1 吸収)
 *
 * `FirExpressionEvaluator` 等の evaluator internal API は minor 間 drift が激しいため
 * 使用しない。
 *
 * ## Preconditions
 *
 * - `expr` は FIR resolution 完了済 (= CHECKERS stage で呼ばれる)。 `const val` 参照の
 *   initializer は module 内なら BODY_RESOLVE 済、 依存 library なら deserialize 済の
 *   literal が得られる想定。 取れない場合は [UserArgValue.UnsupportedExpression] に倒れる
 *   (silent fallback しない)。
 * - 再帰は [MAX_DEPTH] で打ち切り、 超過時は [UserArgValue.UnsupportedExpression]。
 */
internal class ConvertUserArgExpression {

    internal operator fun invoke(expr: FirExpression, compat: CompatContext): UserArgValue =
        convert(expr, compat, depth = 0)

    private fun convert(expr: FirExpression, compat: CompatContext, depth: Int): UserArgValue {
        if (depth > MAX_DEPTH) return unsupported(expr)
        val typeFqn = resolvedTypeFqnOf(expr, compat)
        return when {
            compat.isLiteralExpression(expr) -> {
                val raw = compat.literalValueOrNull(expr) ?: return unsupported(expr)
                UserArgValue.wrapLiteralValue(raw, expectedTypeFqn = typeFqn) ?: unsupported(expr)
            }
            expr is FirGetClassCall -> convertGetClassCall(expr, compat)
            // named argument (`note = "x"`) / spread の wrapper。 resolved mapping 経由でも
            // 残ることがあるため中身を剥がして再帰する。
            expr is FirWrappedArgumentExpression -> convert(expr.expression, compat, depth + 1)
            // vararg parameter へ渡された引数列 (resolution が vararg を 1 node に束ねた形)。
            expr is FirVarargArgumentsExpression ->
                convertElements(expr, expr.arguments, compat, depth)
            expr is FirFunctionCall -> convertFunctionCall(expr, typeFqn, compat, depth)
            // 配列 literal。 型名 (FirArrayLiteral / FirCollectionLiteral) は 2.4 rename drift
            // があるため参照せず、 FirCall + array 型で構造判定する (KDoc 参照)。
            expr is FirCall && typeFqn in ARRAY_TYPE_FQNS ->
                convertElements(expr, expr.arguments, compat, depth)
            expr is FirQualifiedAccessExpression -> convertQualifiedAccess(expr, typeFqn, compat, depth)
            else -> unsupported(expr)
        }
    }

    private fun convertGetClassCall(expr: FirGetClassCall, compat: CompatContext): UserArgValue {
        val classFqn = expr.arguments.firstOrNull()
            ?.let { compat.resolvedTypeOrNullOf(it) }
            ?.let { compat.classIdOfType(it) }
            ?.asSingleFqName()?.asString()
        return if (classFqn != null) UserArgValue.ClassRef(classFqn) else unsupported(expr)
    }

    private fun convertFunctionCall(
        expr: FirFunctionCall,
        typeFqn: String?,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        val resolved = expr.calleeReference.toResolvedCallableSymbol() as? FirCallableSymbol<*>
            ?: return unsupported(expr)
        return when {
            // nested annotation (`Meta(note = "hello")`) は constructor call として現れる。
            resolved is FirConstructorSymbol -> convertAnnotationCall(expr, typeFqn, compat, depth)
            // `-42L` / `-1.5` は receiver literal への unaryMinus 呼び出し。 receiver を
            // 再帰変換して負値化し、 outer の resolved type で narrow し直す。
            resolved.callableId?.callableName?.asString() == "unaryMinus" ->
                convertUnaryMinus(expr, typeFqn, compat, depth)
            else -> unsupported(expr)
        }
    }

    private fun convertUnaryMinus(
        expr: FirFunctionCall,
        typeFqn: String?,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        val receiver = expr.explicitReceiver ?: return unsupported(expr)
        val raw = rawLiteralOf(convert(receiver, compat, depth + 1)) ?: return unsupported(expr)
        val negated: Any = when (raw) {
            is Int -> -raw
            is Long -> -raw
            is Float -> -raw
            is Double -> -raw
            else -> return unsupported(expr)
        }
        return UserArgValue.wrapLiteralValue(negated, expectedTypeFqn = typeFqn) ?: unsupported(expr)
    }

    private fun convertAnnotationCall(
        expr: FirFunctionCall,
        typeFqn: String?,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        val classFqn = typeFqn ?: return unsupported(expr)
        val mapping = (expr.argumentList as? FirResolvedArgumentList)?.mapping
            ?: return unsupported(expr)
        val args = linkedMapOf<String, UserArgValue>()
        for ((argExpr, parameter) in mapping) {
            val converted = convert(argExpr, compat, depth + 1)
            // 一部引数でも変換不能なら annotation 全体を unsupported に倒す (IR 側で
            // 引数だけ default になった "半端な" nested annotation を作らないため)。
            if (converted is UserArgValue.UnsupportedExpression) return unsupported(expr)
            args[parameter.name.asString()] = converted
        }
        return UserArgValue.AnnotationValue(classFqn, args)
    }

    private fun convertQualifiedAccess(
        expr: FirQualifiedAccessExpression,
        typeFqn: String?,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        val resolved = expr.calleeReference.toResolvedCallableSymbol() as? FirCallableSymbol<*>
            ?: return unsupported(expr)
        return when {
            resolved is FirEnumEntrySymbol -> {
                // task-075: 2.3.0 で `callableId` は nullable 化されたため safe call。
                val entryFqn = resolved.callableId?.asSingleFqName()?.asString()
                    ?: return unsupported(expr)
                UserArgValue.EnumRef(entryFqn)
            }
            resolved is FirPropertySymbol -> convertConstProperty(expr, resolved, typeFqn, compat, depth)
            else -> unsupported(expr)
        }
    }

    /**
     * `const val` 単純参照 (`t = NAME` / `i = BASE`) を initializer literal に畳み込む。
     * const chain (`const val A = B`) も再帰で辿れる。 const でない・initializer が
     * 取れない場合は [UserArgValue.UnsupportedExpression]。
     */
    private fun convertConstProperty(
        expr: FirQualifiedAccessExpression,
        symbol: FirPropertySymbol,
        typeFqn: String?,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        // `resolvedStatus` / `resolvedInitializer` は symbol member (bytecode 安定を javap で
        // 確認済) だが、 lazy resolve が絡む経路のため失敗を checker crash にせず unsupported
        // warning に倒す (unresolved-callee crash fix (9a08548) と同じ防御方針)。
        val initializer = runCatching {
            if (!symbol.resolvedStatus.isConst) null else symbol.resolvedInitializer
        }.getOrNull() ?: return unsupported(expr)
        val raw = rawLiteralOf(convert(initializer, compat, depth + 1)) ?: return unsupported(expr)
        // initializer 側の literal 表現 (K2 integer literal の Long 内部表現等) を、 参照元
        // expression の resolved type で narrow し直す。
        return UserArgValue.wrapLiteralValue(raw, expectedTypeFqn = typeFqn) ?: unsupported(expr)
    }

    private fun convertElements(
        whole: FirExpression,
        elements: List<FirExpression>,
        compat: CompatContext,
        depth: Int,
    ): UserArgValue {
        val converted = elements.map { element ->
            val value = convert(element, compat, depth + 1)
            // 要素 1 つでも変換不能なら配列全体を unsupported に倒す (部分的に default 要素が
            // 混ざった配列を作らないため)。
            if (value is UserArgValue.UnsupportedExpression) return unsupported(whole)
            value
        }
        return UserArgValue.ArrayValue(converted)
    }

    private fun resolvedTypeFqnOf(expr: FirExpression, compat: CompatContext): String? =
        compat.resolvedTypeOrNullOf(expr)
            ?.let { compat.classIdOfType(it) }
            ?.asSingleFqName()?.asString()

    /** primitive / String branch の raw 値を取り出す。 それ以外は `null`。 */
    private fun rawLiteralOf(value: UserArgValue): Any? = when (value) {
        is UserArgValue.BoolValue -> value.value
        is UserArgValue.CharValue -> value.value
        is UserArgValue.ByteValue -> value.value
        is UserArgValue.ShortValue -> value.value
        is UserArgValue.IntValue -> value.value
        is UserArgValue.LongValue -> value.value
        is UserArgValue.FloatValue -> value.value
        is UserArgValue.DoubleValue -> value.value
        is UserArgValue.StringValue -> value.value
        else -> null
    }

    /**
     * 変換不能な式を [UserArgValue.UnsupportedExpression] に落とす。 warning の `{0}` に
     * 埋める description は source text (空白圧縮 + [MAX_DESCRIPTION_LENGTH] 打ち切り)、
     * 取れない場合は FIR node 名。 warning の発火自体は IR 側
     * (`BuildUserArgPrimitive` + `UserArgWarnings.EXPRESSION_UNSUPPORTED`) で行う。
     */
    private fun unsupported(expr: FirExpression): UserArgValue.UnsupportedExpression {
        val sourceText = sourceTextOf(expr)
            ?.replace(WHITESPACE_REGEX, " ")
            ?.take(MAX_DESCRIPTION_LENGTH)
        return UserArgValue.UnsupportedExpression(
            description = sourceText ?: (expr::class.simpleName ?: "expression"),
        )
    }

    /**
     * 式の source text を取り出す。 K2 CLI compile は LightTree parser (= PSI なし) が
     * 既定のため、 PSI 経路 ([KtPsiSourceElement]) に加えて
     * `KtSourceElement.treeStructure.toString(lighterASTNode)` の LightTree 経路も試す
     * (どちらも 2.0.0 - 2.4.20-RC で bytecode 安定を javap で確認済)。 取れなければ `null`。
     */
    private fun sourceTextOf(expr: FirExpression): String? {
        val source = expr.source ?: return null
        (source as? KtPsiSourceElement)?.psi?.text?.let { return it }
        return runCatching { source.treeStructure.toString(source.lighterASTNode).toString() }
            .getOrNull()
    }

    private companion object {
        /** const chain / nested annotation / 配列の再帰変換の深さ上限。 */
        const val MAX_DEPTH = 8

        /** warning description に埋める source snippet の最大長。 */
        const val MAX_DESCRIPTION_LENGTH = 60

        val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * annotation parameter に指定できる array 型 FqN。 配列 literal の構造判定
         * (FirCall + この型) に使う。
         */
        val ARRAY_TYPE_FQNS = setOf(
            "kotlin.Array",
            "kotlin.IntArray",
            "kotlin.LongArray",
            "kotlin.ShortArray",
            "kotlin.ByteArray",
            "kotlin.BooleanArray",
            "kotlin.CharArray",
            "kotlin.FloatArray",
            "kotlin.DoubleArray",
        )
    }
}
