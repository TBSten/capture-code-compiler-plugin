package me.tbsten.capture.code.compat

import java.util.concurrent.CopyOnWriteArraySet

/**
 * `@CaptureCode` メタアノテーションが付いた annotation class の FqN 集合 (Logic A の SSOT)。
 *
 * Phase 2 task 2.1 で `K200CapturedSourcesCollector.HARDCODED_MARKER_FQNS` などの hardcoded list を
 * 撤廃し、本 registry を後続 logic (B-ir / F / G / H) が共通参照する形に集約する。
 *
 * ## ライフサイクル
 *
 * 1 回のコンパイル (= 1 `IrGenerationExtension.generate` 呼び出し) の間だけデータを保持する
 * **compilation-scoped mutable holder**。次のコンパイルが始まる前に [reset] で必ずクリアする。
 *
 * - **FIR phase**: `CaptureCodeFirExtensionRegistrar` が登録する FIR session component
 *   (`CaptureCodeFirMarkerService`) と declaration checker (`CaptureCodeMarkerClassChecker`) が
 *   `@CaptureCode` メタ付き annotation class の FqN を発見次第 [registerMarker] で追加する。
 * - **IR phase**: `CaptureCodeIrExtension` (および compat-kXXXX の `IrInjector`) が
 *   [markerFqns] を読み、書き換え対象の marker かどうかを判定する。
 *
 * ## なぜ FIR session ではなく compat module の object か
 *
 * `IrPluginContext` は `FirSession` を公開しておらず、FIR session component を IR phase から直接
 * 参照する手段が安定 API として存在しない (`Fir2IrPluginContext` は internal API)。
 * そこで「FIR phase の登録結果を IR phase が読み取る」という 1 方向の受け渡しに限定し、
 * 軽量な module-shared holder で表現する。
 *
 * SSOT は本 object のみ。FIR / IR / compat-kXXXX 各層は本 object を直接参照すること
 * (collector / rewriter 内で hardcoded list を再定義しないこと)。
 *
 * ## 並行性
 *
 * Kotlin compiler の plugin 拡張は同一 thread で sequential に呼ばれるのが基本だが、
 * 安全側に倒すため [CopyOnWriteArraySet] を採用する。read が圧倒的に多いユースケース
 * (FIR で N 個 register → IR で繰り返し read) に向く。
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic A / §6 Phase ordering を参照。
 */
public object CaptureCodeMarkerRegistry {

    private val markers: MutableSet<String> = CopyOnWriteArraySet()

    /**
     * `@CaptureCode` メタ付き annotation class として検出された FqN の集合 (read-only view)。
     *
     * 戻り値は snapshot ではなく live view (CopyOnWriteArraySet がそのまま iterate 安全な参照を返す)
     * のため、registration と並行して iterate しても [ConcurrentModificationException] にはならない。
     */
    public val markerFqns: Set<String>
        get() = markers

    /**
     * marker annotation の FqN を登録する。
     *
     * 既に登録済みの FqN を再度 register しても副作用は無い (set semantics)。
     *
     * @param fqn marker annotation class の完全修飾名 (例: `com.example.Snippets`)
     */
    public fun registerMarker(fqn: String) {
        markers.add(fqn)
    }

    /**
     * 与えられた FqN が登録済みの marker かどうかを返す。
     *
     * IR phase で annotation の type 経由で marker 判定を行う際の hot path。
     */
    public fun isMarker(fqn: String): Boolean = fqn in markers

    /**
     * registry を空にする。テストおよび `IrGenerationExtension.generate` 完了時に呼ぶ。
     *
     * 1 つの ClassLoader で複数コンパイル (例: kctfork で連続 compile) を行う場合、
     * 前回コンパイルの marker FqN が次回に漏れないようにするために必要。
     */
    public fun reset() {
        markers.clear()
    }
}
