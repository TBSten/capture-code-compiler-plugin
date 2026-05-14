package me.tbsten.capture.code

import me.tbsten.capture.code.feature.capturedsources.checker.CapturedSourcesCallCheckersExtension
import me.tbsten.capture.code.feature.expression_annotation.ExpressionAnnotationCheckersExtension
import me.tbsten.capture.code.fir.checker.CaptureCodeFirAdditionalCheckersExtension
import me.tbsten.capture.code.fir.marker.CaptureCodeFirMarkerService
import me.tbsten.capture.code.fir.marker.CaptureCodeMarkerCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Capture Code compiler plugin の FIR 拡張点エントリ。
 *
 * Logic A:
 *
 * - [CaptureCodeFirMarkerService]: `@CaptureCode` メタ付き annotation class の `ClassId` を保持する
 *   session component。後段 (B-fir / F / G / H) が参照する SSOT
 * - [CaptureCodeMarkerCheckersExtension]: FIR check phase で全 annotation class を訪問し、
 *   meta-annotation の有無を判定して service に登録する `FirAdditionalCheckersExtension`
 *
 * Logic F:
 *
 * - [CaptureCodeFirAdditionalCheckersExtension]: marker annotation の制約違反 (visibility /
 *   @Retention / @Target / parameter 型 / filler default / expect annotation) を診断する
 *   `FirAdditionalCheckersExtension`
 *
 * Logic G:
 *
 * - [CapturedSourcesCallCheckersExtension]: `capturedSources<T>()` 呼び出しの型引数 `T` が
 *   `@CaptureCode` メタ付き marker class であることを検査する expression checker
 *
 * Logic B-fir:
 *
 * - [ExpressionAnnotationCheckersExtension]: `@Marker (expr)` 形の式 annotation を
 *   FIR phase で観察し、`CaptureCodeExpressionSiteRegistry` に
 *   `(filePath, startOffset, endOffset, markerFqn, userArgs)` を push する
 *   `FirBasicExpressionChecker` を登録する。IR phase では式 annotation が残らない (spike で
 *   観測済) ため、FIR session storage 経由で IR phase に bridge する経路の FIR 側エントリ。
 *
 * 詳細は `compiler-plugin-design.md` §5 / §6 を参照。
 */
public class CaptureCodeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::CaptureCodeFirMarkerService
        +::CaptureCodeMarkerCheckersExtension
        +::CaptureCodeFirAdditionalCheckersExtension
        +::CapturedSourcesCallCheckersExtension
        +::ExpressionAnnotationCheckersExtension
    }
}
