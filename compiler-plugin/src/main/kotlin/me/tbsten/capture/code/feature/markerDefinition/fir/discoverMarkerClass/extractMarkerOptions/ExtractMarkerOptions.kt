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
