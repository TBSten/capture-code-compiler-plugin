package me.tbsten.capture.code

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
 * 後続 ticket で追加予定の extension:
 * - `capturedSources<T>()` 診断 (Logic G: task-011): T が `@CaptureCode` メタ修飾されているか
 * - ターゲットノード位置の事前収集 (Logic B-fir): 式 annotation の offset 確保 (task-017)
 *
 * 詳細は `compiler-plugin-design.md` §5 / §6 を参照。
 */
public class CaptureCodeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::CaptureCodeFirMarkerService
        +::CaptureCodeMarkerCheckersExtension
        +::CaptureCodeFirAdditionalCheckersExtension
    }
}
