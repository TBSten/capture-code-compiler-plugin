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
 * Phase 2 task 2.1 (Logic A) で以下を登録する:
 *
 * - [CaptureCodeFirMarkerService]: `@CaptureCode` メタ付き annotation class の `ClassId` を保持する
 *   session component。後段 (B-fir / F / G / H) が参照する SSOT
 * - [CaptureCodeMarkerCheckersExtension]: FIR check phase で全 annotation class を訪問し、
 *   meta-annotation の有無を判定して service に登録する `FirAdditionalCheckersExtension`
 *
 * Phase 2 task 2.2 (Logic F, task-010) で追加:
 *
 * - [CaptureCodeFirAdditionalCheckersExtension]: marker annotation の制約違反 (visibility /
 *   @Retention / @Target / parameter 型 / filler default / expect annotation) を診断する
 *   `FirAdditionalCheckersExtension`
 *
 * Phase 2 task 2.6 (Logic G, task-011) で追加:
 *
 * - [CapturedSourcesCallCheckersExtension]: `capturedSources<T>()` 呼び出しの型引数 `T` が
 *   `@CaptureCode` メタ付き marker class であることを検査する expression checker
 *
 * Phase 2 task 2.9 (Logic B-fir, task-017) で追加:
 *
 * - [ExpressionAnnotationCheckersExtension]: `@Marker (expr)` 形の式 annotation を
 *   FIR phase で観察し、`CaptureCodeExpressionSiteRegistry` に
 *   `(filePath, startOffset, endOffset, markerFqn, userArgs)` を push する
 *   `FirBasicExpressionChecker` を登録する。task-009 spike (R1 confirmed: IR phase で式
 *   annotation は残らない) を受けて FIR session storage 経由で IR phase に bridge する経路の
 *   FIR 側エントリーポイント。
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
