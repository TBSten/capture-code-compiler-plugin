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
     * `IrGetClass` に再構築する候補 (現状未対応 → `BuildUserArgPrimitive` が `null` IR を返す)。
     */
    public data class ClassRef(public val classFqn: String) : UserArgValue()

    /**
     * enum entry の完全修飾名 (例: `com.example.Verb.GET`)。 IR phase で
     * [me.tbsten.capture.code.compat.CompatContext.newIrGetEnumValue] に再構築。
     */
    public data class EnumRef(public val entryFqn: String) : UserArgValue()

    // 将来 nested annotation / Array の case はここに追加。

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
