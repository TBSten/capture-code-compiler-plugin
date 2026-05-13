package me.tbsten.capture.code

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Capture Code compiler plugin の FIR 拡張点エントリ。
 *
 * Phase 1 (本 ticket) では FIR 側で実際に検出する logic は無いため `configurePlugin` 内は空。
 * 後続 ticket で次のような extension を登録する受け皿として機能する:
 *
 * - メタアノテーション認識 (Logic A): `FirSupertypeGenerationExtension` 等を介した session 共有 storage
 * - marker annotation 診断 (Logic F) / `capturedSources<T>()` 診断 (Logic G): `FirAdditionalCheckersExtension`
 * - ターゲットノード位置の事前収集 (Logic B-fir): session-scoped storage への蓄積
 *
 * 詳細は `compiler-plugin-design.md` §5 / §6 を参照。
 */
public class CaptureCodeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // Phase 1 では FIR extension の登録経路を確保するのみ。後続 ticket で追加する。
    }
}
