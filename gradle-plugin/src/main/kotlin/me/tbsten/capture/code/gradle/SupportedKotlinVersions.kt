package me.tbsten.capture.code.gradle

/**
 * SSOT for the range of Kotlin versions that the CaptureCode compiler plugin officially
 * supports.
 *
 * 戦略 B (compat module 分離) を採用しているため、 各 `compat-kXXX` module が実 dispatch を
 * 担当する (`META-INF/services` 経由 ServiceLoader 解決)。 Gradle plugin 側の責務は **version
 * 検出と warn / error** のみ:
 *
 * - `kotlinVersion < [MIN_SUPPORTED_VERSION]` (例: Kotlin 1.9 以前) → **`GradleException`** で
 *   即座に build を停止 (FIR / IR API が compat レイヤと互換でない)。
 * - `kotlinVersion in [MIN_SUPPORTED_VERSION] until [MAX_TESTED_VERSION_EXCLUSIVE]` →
 *   正常動作対象 (warn なし)。
 * - `kotlinVersion >= [MAX_TESTED_VERSION_EXCLUSIVE]` → warn ログのみ (本 plugin と
 *   compat module 群がまだ verify されていない新 major / minor だが、 build 自体は続行)。
 *
 * 新バージョン追加時の手順:
 * 1. `:compiler-plugin:compat-kXYZ` module を追加 (compat 実装 + Factory 登録)
 * 2. `kotlin-compiler-embeddable-kXYZ` を `libs.versions.toml` に追加
 * 3. CI matrix に新 version エントリを追加
 * 4. 本ファイルの [MAX_TESTED_VERSION_EXCLUSIVE] を **新 version の次** に bump
 *
 * Single source of truth: 本ファイル。 `gradle-plugin` module 内に閉じている (= `:compat` への
 * compileClasspath 依存を持ち込まないこと優先)。
 */
internal object SupportedKotlinVersions {

    /**
     * 最低サポート Kotlin バージョン (inclusive)。
     *
     * このバージョン未満で plugin を apply すると [io.github.tbsten] compiler plugin の
     * FIR / IR API が壊滅的に異なる (K1 vs K2 など) ため、 即 build error にする。
     */
    const val MIN_SUPPORTED_VERSION: String = "2.0.0"

    /**
     * 検証済み Kotlin バージョン上限 (exclusive)。
     *
     * 現在は `compat-k200` (Kotlin 2.0.x) と `compat-k210` (Kotlin 2.1.x) が同梱されており、
     * 2.2.0 以降は **未検証**。 ユーザ project が 2.2.0+ を使う場合は warn を出すが、
     * `compat-k210` の Factory が dispatch される (= 動く可能性は高いが保証なし)。
     */
    const val MAX_TESTED_VERSION_EXCLUSIVE: String = "2.2.0"
}
