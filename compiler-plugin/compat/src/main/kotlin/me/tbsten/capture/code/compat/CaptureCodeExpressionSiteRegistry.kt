package me.tbsten.capture.code.compat

import java.util.concurrent.CopyOnWriteArrayList

/**
 * task-009 spike で確定した「式 annotation の offset を FIR phase で確保 → IR phase で読み出す」
 * 経路 (design §5 Logic B-fir) のための **process-scoped holder**。
 *
 * Kotlin 2.0 の IR phase では式 annotation (`FirAnnotation`) が残らないため (task-009 spike
 * 観察 (b))、FIR phase で `(filePath, startOffset, endOffset, markerFqn, userArgs)` を本 registry に
 * push し、IR phase の collector が `sitesFor(markerFqn)` で読み出して `CapturedSite(kind = EXPRESSION)`
 * に変換する。
 *
 * ## ライフサイクル
 *
 * [CaptureCodeMarkerRegistry] と同じく **1 回のコンパイル単位** で生存する compilation-scoped
 * mutable holder。`CaptureCodeIrExtension.generate` 完了時に [reset] でクリアする。
 *
 * ## なぜ FIR session ではなく compat module の object か
 *
 * [CaptureCodeMarkerRegistry] と同じ理由:
 * - `IrPluginContext` は `FirSession` を公開しておらず、FIR session component を IR phase から
 *   直接参照する手段が安定 API として無い
 * - 「FIR phase の登録結果を IR phase が読み取る」という 1 方向の受け渡しに限定する用途では
 *   軽量な module-shared holder が自然
 *
 * ## userArgs の表現
 *
 * marker constructor の filler 以外のパラメータ (ユーザ定義) は、宣言 annotation では IR の
 * `IrConstructorCall` から `getValueArgument(i)` で取り出せたが (task-014)、式 annotation の場合は
 * IR まで届かないため、FIR の `FirAnnotation.argumentMapping` を **そのまま** 渡す経路は使えない。
 *
 * 現在の対応:
 * - FIR phase で `FirAnnotation` から名前 → リテラル値 (`Any?`) の map を抜き、本 registry に push
 * - IR phase で対応する `IrConstructorCall` 引数を **新規構築** する (primitive のみ最小実装)
 *
 * **本 ticket scope では、ケース #67 (`Both_Case67` = filler のみ + EXPRESSION kind)** のような
 * filler だけが入った marker を確実にサポートする。primitive 以外を含む marker でも、collector が
 * 引数を読まなければ default 値で marker instance が組み立てられるため、最低限は動作する。
 *
 * 詳細は `task-017` 完了メモを参照。
 */
public object CaptureCodeExpressionSiteRegistry {

    /**
     * 式 annotation 1 件分の登録情報。
     *
     * @property filePath FIR の `KtSourceElement` から取り出した file path (絶対パス想定)。
     *                    IR phase で `IrFile.fileEntry.name` と照合してキャプチャ対象の file を特定する。
     * @property startOffset annotation **対象** expression の startOffset (annotation marker 自身ではない)。
     * @property endOffset annotation 対象 expression の endOffset。
     * @property markerFqn marker annotation class の完全修飾名 ([CaptureCodeMarkerRegistry] と整合)。
     * @property userArgs filler 以外のパラメータの名前 → 値 (`String`/`Int`/`Boolean`/`enum FqN`) の map。
     *                    現状 primitive と enum (FqN 文字列) のみサポート (本 ticket scope)。
     */
    public data class Site(
        val filePath: String,
        val startOffset: Int,
        val endOffset: Int,
        val markerFqn: String,
        val userArgs: Map<String, Any?> = emptyMap(),
    )

    private val sites: MutableList<Site> = CopyOnWriteArrayList()

    /**
     * 登録された全 site の read-only view。read order は登録順 (= FIR の検査順 ≒ source 順) に
     * 沿う `CopyOnWriteArrayList` の自然な iteration order に従う。
     */
    public val allSites: List<Site>
        get() = sites

    /**
     * site を追加する。同じ `(filePath, startOffset, endOffset, markerFqn)` の組み合わせは
     * 既に登録済みでも追加される (FIR checker が同じ expression を複数回訪問する可能性は低いが、
     * 念のためすべての訪問を残す方が deterministic な振る舞いに繋がる)。重複は IR phase 側で
     * 必要に応じて distinct 化する。
     */
    public fun addSite(site: Site) {
        sites.add(site)
    }

    /**
     * 指定 [markerFqn] と [filePath] にマッチする site を、`startOffset` 昇順で返す。
     *
     * IR phase の `K2000CapturedSourcesCollector` (file 単位で走る) からは file path を指定して
     * 取り出すため、本 method は **file + marker** の 2 軸で filter する。
     */
    public fun sitesFor(markerFqn: String, filePath: String): List<Site> =
        sites.filter { it.markerFqn == markerFqn && it.filePath == filePath }
            .sortedBy { it.startOffset }

    /**
     * registry を空にする。`CaptureCodeIrExtension.generate` 完了時 (try/finally) に呼ぶ。
     * テストでも各テストの境界で reset すること。
     */
    public fun reset() {
        sites.clear()
    }
}
