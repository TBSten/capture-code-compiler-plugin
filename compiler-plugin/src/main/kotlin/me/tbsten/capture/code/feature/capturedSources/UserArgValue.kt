package me.tbsten.capture.code.feature.capturedSources

/**
 * sum type for marker annotation **user argument values** that are
 * carried from FIR phase (Logic B-fir) to IR phase
 * (`BuildUserArgPrimitive`) for expression-origin re-materialisation.
 *
 * Replaces the prior `Any?` value type in
 * [CaptureCodeExpressionSiteRegistry.Site.userArgs] and
 * [me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.CollectedSite.expressionUserArgs].
 *
 * Each subclass corresponds to one branch in
 * `BuildUserArgPrimitive.invoke`'s `when` dispatch.
 *
 * ## なぜ sealed か
 *
 * 旧 `Any?` 経路は writer (`CollectExpressionSite.collectUserArgs`) と
 * reader (`BuildUserArgPrimitive.invoke`) で「primitive / String / enum FqN /
 * class FqN」 の暗黙の sum type を共有していたが、 型に現れず exhaustive チェックが
 * 効かなかった。 sealed 化することで:
 *
 * - reader 側の `when` が exhaustive コンパイラチェックの対象になる
 * - String が「enum FqN」 を兼ねていた多重意味を [EnumRef] / [ClassRef] / [StringValue]
 *   に分離し、 silent failure (例: enum resolve 失敗を null 化して握り潰す) を warning 化
 *   する足場ができる (task-134 で warning 化予定)
 */
public sealed class UserArgValue {
    /** `null` リテラルの引数値、 もしくは解決失敗時の fallback。 */
    public object NullValue : UserArgValue()

    public data class BoolValue(public val value: Boolean) : UserArgValue()
    public data class CharValue(public val value: Char) : UserArgValue()
    public data class ByteValue(public val value: Byte) : UserArgValue()
    public data class ShortValue(public val value: Short) : UserArgValue()
    public data class IntValue(public val value: Int) : UserArgValue()
    public data class LongValue(public val value: Long) : UserArgValue()
    public data class FloatValue(public val value: Float) : UserArgValue()
    public data class DoubleValue(public val value: Double) : UserArgValue()
    public data class StringValue(public val value: String) : UserArgValue()

    /**
     * `SomeClass::class` の class FqN (例: `com.example.MySnippet`)。 IR phase で
     * `IrClassReference` に再構築する (bug-004: `BuildUserArgPrimitive` の
     * `IrClassReferenceShim` 経由。 全 baseline で bytecode signature が同一であることを
     * javap で確認済)。
     */
    public data class ClassRef(public val classFqn: String) : UserArgValue()

    /**
     * enum entry の完全修飾名 (例: `com.example.Verb.GET`)。 IR phase で
     * [me.tbsten.capture.code.compat.CompatContext.newIrGetEnumValue] に再構築。
     */
    public data class EnumRef(public val entryFqn: String) : UserArgValue()

    /**
     * 配列 literal (`["a", "b"]` / `[1, 2]`) の要素列。 IR phase で
     * [me.tbsten.capture.code.compat.CompatContext.newIrVararg] に再構築。
     *
     * bug-004: 従来 branch 自体が存在せず silent に default `[]` へ落ちていた経路を
     * expression 起源でも実値化する。
     */
    public data class ArrayValue(public val elements: List<UserArgValue>) : UserArgValue()

    /**
     * nested annotation (`meta = Meta(note = "hello")`) の class FqN と引数 map。 IR phase で
     * [me.tbsten.capture.code.compat.CompatContext.newIrConstructorCall] +
     * `putCallValueArgument` に再構築。 [args] の value は再帰的に [UserArgValue]。
     *
     * bug-004: 従来 `FirQualifiedAccessExpression` branch が constructor call を enum 扱いして
     * `Could not resolve enum entry 'example.Meta.Meta'` という誤 warning + default fallback に
     * なっていた経路を expression 起源でも実値化する。
     */
    public data class AnnotationValue(
        public val classFqn: String,
        public val args: Map<String, UserArgValue>,
    ) : UserArgValue()

    /**
     * FIR phase で [UserArgValue] に変換できなかった引数式。 IR phase
     * (`BuildUserArgPrimitive`) で `CC_USERARG_EXPRESSION_UNSUPPORTED` warning を発火して
     * default fallback する。
     *
     * bug-004: 従来は「解決できない enum entry」 という誤 warning (もしくは Array の
     * silent fallback) になっていた複合定数式 (`BASE * 2 + 1` 等) / 非 const 参照を、
     * 実態に合った文面の warning に昇格するための branch。 [description] は warning の
     * `{0}` に埋める source snippet (PSI text が取れない場合は FIR node 名)。
     */
    public data class UnsupportedExpression(public val description: String) : UserArgValue()

    public companion object Companion {
        /**
         * `FirLiteralExpression.value` (= `Any?`) を対応する [UserArgValue] subclass に
         * ラップする。 各 compat-kXXX の [me.tbsten.capture.code.compat.CompatContext.literalValueOrNull]
         * 実装が共通で使う SSoT。 未対応 primitive (BigDecimal 等) は `null` を返す
         * (= caller 側は `?: UserArgValue.NullValue` で統合する想定)。
         *
         * task-133: sealed 化に伴い導入。 旧 `Any?` 経路から sealed 経路への単一 conversion
         * point として、 各 compat-kXXX が `when (raw) { is Int -> IntValue(raw); ... }` を
         * 再実装しなくて済むようにする。
         *
         * task-charter-5-userarg-numeric-coerce (2026-05-21): [expectedTypeFqn] が
         * `kotlin.Byte` / `kotlin.Short` / `kotlin.Int` / `kotlin.Float` のいずれかで、 raw が
         * Long / Double (= K2 FIR の integer/float literal の internal representation で起こりやすい
         * widening) の場合は、 expected type に合わせて narrow する。 この coercion を入れないと
         * IR 再構築段階で wrong-typed `IrConst` が emit され、 JVM backend が integer slot に
         * long を積んだ bytecode を生成して `VerifyError: Bad type on operand stack` を起こす。
         */
        public fun wrapLiteralValue(raw: Any, expectedTypeFqn: String? = null): UserArgValue? {
            // K2 FIR integer literal narrowing (Long -> Byte/Short/Int) と
            // float literal narrowing (Double -> Float) を expected type で揃える。
            val coerced: Any = when (expectedTypeFqn) {
                "kotlin.Byte" -> when (raw) {
                    is Long -> raw.toByte()
                    is Int -> raw.toByte()
                    is Short -> raw.toByte()
                    else -> raw
                }
                "kotlin.Short" -> when (raw) {
                    is Long -> raw.toShort()
                    is Int -> raw.toShort()
                    else -> raw
                }
                "kotlin.Int" -> when (raw) {
                    is Long -> raw.toInt()
                    else -> raw
                }
                "kotlin.Float" -> when (raw) {
                    is Double -> raw.toFloat()
                    else -> raw
                }
                else -> raw
            }
            return when (coerced) {
                is Boolean -> BoolValue(coerced)
                is Char -> CharValue(coerced)
                is Byte -> ByteValue(coerced)
                is Short -> ShortValue(coerced)
                is Int -> IntValue(coerced)
                is Long -> LongValue(coerced)
                is Float -> FloatValue(coerced)
                is Double -> DoubleValue(coerced)
                is String -> StringValue(coerced)
                else -> null
            }
        }
    }
}
