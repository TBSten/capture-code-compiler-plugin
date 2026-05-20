package me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.extractMarkerOptions

import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerOptions
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.Name

/**
 * `@CaptureCode(...)` annotation instance の `argumentMapping` から
 * [CaptureCodeMarkerOptions] を構築する drift-free ヘルパ (sub-logic of Logic A)。
 *
 * Override enum value (`me.tbsten.capture.code.CaptureCode.Override.Default / Yes / No`) は
 * FIR では `FirPropertyAccessExpression` (もしくは派生の `FirQualifiedAccessExpression`)
 * として resolved されるため、 callee の `FirCallableSymbol.callableId` から `Default` / `Yes` / `No`
 * の simple name を取り出す。 解決できない引数 / 未知の名前は [CaptureCodeMarkerOptions.Override.Default]
 * (= override なし) として扱う。
 *
 * FIR API は本処理範囲では drift しない (FirGetClassCall / FirPropertyAccessExpression /
 * FirQualifiedAccessExpression / `toResolvedCallableSymbol()` / `FirCallableSymbol.callableId`
 * のすべてが 2.0.x / 2.1.x / 2.2.x で互換)。 そのため main module に置き、 全 compat-kXXX
 * から共有する SSOT として扱える。
 *
 * ## Preconditions
 *
 * Caller (= [me.tbsten.capture.code.feature.markerDefinition.fir.discoverMarkerClass.DiscoverMarkerClass]
 * / [me.tbsten.capture.code.feature.markerDefinition.fir.validateMarkerAnnotation.warnIfOverrideNoEffect.WarnIfOverrideNoEffect])
 * は以下を保証する責務がある。 違反した場合の挙動は「未解決の override 引数は [CaptureCodeMarkerOptions.Override.Default]
 * 扱い (silent fallback)」 で、 fail-fast はしない (= marker 動作が無効化されないことを優先)。
 *
 * - `annotation` は `@CaptureCode`-meta annotation の FIR-resolved instance。
 *   typical root cause: caller が non-meta annotation を渡している (= `DiscoverMarkerClass`
 *   側で meta annotation チェックを通っていない bug)。
 * - `annotation.argumentMapping.mapping` は Kotlin annotation argument のキー
 *   ([Name]) と FIR-resolved expression の対応を保持する。 K2 では `mapping` の値は
 *   `FirGetClassCall` / `FirPropertyAccessExpression` / `FirQualifiedAccessExpression` /
 *   `FirLiteralExpression` のいずれか (= annotation 引数として合法な FIR 形態)。
 * - argument expression の callee が enum entry を指す場合、 その `callableId` は
 *   `Default` / `Yes` / `No` を simpleName に持つ (= `CaptureCode.Override` の entries)。
 *   typical root cause: marker 定義が runtime 側の `CaptureCode.Override` enum と
 *   不整合 (= `:annotation` モジュールが古い)。 未知の simple name は `Default` 扱い。
 *
 * `require(...)` は導入していない。 想定外の expression 形態 / 未知 enum entry でも
 * `Default` fallback で marker 機能の bare-minimum 動作を維持する方が、 plugin 開発者の
 * 体験として safer。
 */
public class ExtractMarkerOptions {

    /**
     * 与えられた `@CaptureCode(...)` annotation の argument mapping から
     * [CaptureCodeMarkerOptions] を抽出する。
     *
     * argument mapping が空 (= 引数なしの `@CaptureCode` marker) の場合は
     * [CaptureCodeMarkerOptions.DEFAULT] を返す。
     */
    public operator fun invoke(annotation: FirAnnotation): CaptureCodeMarkerOptions {
        val mapping = annotation.argumentMapping.mapping
        if (mapping.isEmpty()) return CaptureCodeMarkerOptions.DEFAULT

        fun read(name: String): CaptureCodeMarkerOptions.Override {
            val expr = mapping[Name.identifier(name)] ?: return CaptureCodeMarkerOptions.Override.Default
            val entryName = when (expr) {
                is FirGetClassCall -> null
                is FirPropertyAccessExpression -> expr.enumEntryName()
                is FirQualifiedAccessExpression -> expr.enumEntryName()
                else -> null
            } ?: return CaptureCodeMarkerOptions.Override.Default
            return when (entryName) {
                "Yes" -> CaptureCodeMarkerOptions.Override.Yes
                "No" -> CaptureCodeMarkerOptions.Override.No
                else -> CaptureCodeMarkerOptions.Override.Default
            }
        }

        return CaptureCodeMarkerOptions(
            includeKdoc = read("includeKdoc"),
            includeImports = read("includeImports"),
            includeAnnotationLines = read("includeAnnotationLines"),
            dedent = read("dedent"),
            includeLineInfo = read("includeLineInfo"),
        )
    }

    /**
     * `FirPropertyAccessExpression` / `FirQualifiedAccessExpression` が enum entry を指していれば、
     * その enum entry の simple name (`Default` / `Yes` / `No`) を返す。
     *
     * FQN は `me.tbsten.capture.code.CaptureCode.Override.Yes` のような形式なので、
     * `callableId.callableName` がそのまま `Yes` / `No` / `Default` になる。
     */
    private fun FirQualifiedAccessExpression.enumEntryName(): String? {
        val resolved = calleeReference.toResolvedCallableSymbol() as? FirCallableSymbol<*> ?: return null
        // K2.2.x: `FirCallableSymbol.callableId` becomes nullable. Use safe call to absorb the drift
        // — older versions (2.0.x / 2.1.x) return non-null and the safe call is a harmless overhead.
        return resolved.callableId?.callableName?.asString()
    }
}
