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
     * 現状サポート: **Kotlin 2.0.x 〜 2.4.10** が CI matrix (`core-matrix`) で実機検証済み。
     * `compat-k200` / `compat-k202` / `compat-k210` / `compat-k220` / `compat-k230` /
     * `compat-k240` の 6 compat module を通じて FIR / IR API drift を吸収している。
     *
     * 2.4.20 系は pre-release (`2.4.20-RC`) を CI で検証中だが、 stable 昇格までは **未検証扱い**
     * とし、 ユーザ project が 2.4.20+ (stable) を指定した場合は warn を出す (build 自体は続行)。
     * pre-release は base version が同じ stable より小さい ([KotlinVersionParts] の比較規則) ので、
     * `2.4.20-RC` 自体は warn 対象外 = 検証済み扱いになる。
     */
    const val MAX_TESTED_VERSION_EXCLUSIVE: String = "2.4.20"
}
