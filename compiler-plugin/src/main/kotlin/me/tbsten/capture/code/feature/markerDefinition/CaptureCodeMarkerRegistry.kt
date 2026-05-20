package me.tbsten.capture.code.feature.markerDefinition

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
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
 * ## なぜ FIR session ではなく main module の object か
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
 * 安全側に倒すため [CopyOnWriteArraySet] / [CopyOnWriteArrayList] を採用する。read が圧倒的に多い
 * ユースケース (FIR で N 個 register → IR で繰り返し read) に向く。
 *
 * ## task-127: registration 履歴 (duplicate FQN 検出)
 *
 * 単純な `Set<String>` だと同一 FQN を異なる declaration site から複数回 register しても自然に
 * dedup される。 task-127 で導入した `CC_CAPTUREDSOURCES_DUPLICATE_MARKER_FQN` warning は同一
 * compilation 内で同じ marker FqN を 2 回以上 register したときに発火する必要があるため、
 * 各 register 呼び出しを [MarkerRegistration] として履歴 ([registrations]) に積む形に拡張した。
 *
 * - [registerMarker] / [registerMarkerOptions] は **加算のみ** (= 同 FQN の重複 register でも entry
 *   が積まれる)
 * - [markerFqns] / [isMarker] / [markerOptionsFor] は dedup された view を返し、 既存 caller との
 *   後方互換を維持
 * - [duplicateMarkerFqns] で 「同 compilation 内で 2 回以上 register された FQN」 のリストを取得
 *   (IR phase の [WarnIfDuplicateMarkerFqn] 経由で warning を発火)
 *
 * 詳細は `compiler-plugin-design.md` §5 Logic A / §6 Phase ordering を参照。
 */
public object CaptureCodeMarkerRegistry {

    private val markers: MutableSet<String> = CopyOnWriteArraySet()

    /**
     * task-127: 各 register 呼び出しを記録する履歴。 同 FQN を異なる declaration site から
     * 複数回 register したケースを検出するために必要。 ordered list (CopyOnWriteArrayList) を使い、
     * 追加順を保つ (= 後段 [WarnIfDuplicateMarkerFqn] の deterministic ordering のため)。
     */
    private val registrationsList: MutableList<MarkerRegistration> = CopyOnWriteArrayList()

    /**
     * 各 marker FqN に対する per-marker option override の保持テーブル。
     *
     * `@CaptureCode(includeKdoc = Override.Yes, ...)` のように引数付きで宣言された marker は、
     * FIR phase で argument を読んだ後 [registerMarkerOptions] でこのテーブルに保存される。
     * 引数なしの `@CaptureCode` marker は entry を持たない (= IR 側で参照すると
     * [CaptureCodeMarkerOptions.DEFAULT] が返る) ことで、後方互換が保たれる。
     *
     * IR phase の collector が `markerOptionsFor(fqn)` 経由で per-site の effective option を
     * 計算する際に参照する。
     */
    private val markerOptionsTable: MutableMap<String, CaptureCodeMarkerOptions> = ConcurrentHashMap()

    /**
     * `@CaptureCode` メタ付き annotation class として検出された FqN の集合 (read-only view)。
     *
     * 戻り値は snapshot ではなく live view (CopyOnWriteArraySet がそのまま iterate 安全な参照を返す)
     * のため、registration と並行して iterate しても [ConcurrentModificationException] にはならない。
     */
    public val markerFqns: Set<String>
        get() = markers

    /**
     * task-127: register の履歴 (read-only view)。 同 FQN を別 declaration から複数回 register
     * したケースは別 entry として複数回出現する。
     *
     * IR phase の [me.tbsten.capture.code.feature.markerDefinition.ir.warnIfDuplicateMarkerFqn.WarnIfDuplicateMarkerFqn]
     * が duplicate 検出のために走査する。
     */
    public val registrations: List<MarkerRegistration>
        get() = registrationsList

    /**
     * marker annotation の FqN を登録する。
     *
     * 既に登録済みの FqN を再度 register しても [markerFqns] の view では dedup されるが、
     * 履歴 ([registrations]) には毎回 entry が積まれる (task-127 で duplicate 検出に必要)。
     *
     * @param fqn marker annotation class の完全修飾名 (例: `com.example.Snippets`)
     * @param sourceFilePath register 発火元の declaration が含まれる source file path (任意)。
     *   FIR phase からは `CompatContext.containingFilePathOf` で取得した path を渡すと、
     *   duplicate 検出時のエラーメッセージで location が出せて便利。 unit test 等で source を
     *   持たない呼び出しでは `null` でよい。
     */
    public fun registerMarker(fqn: String, sourceFilePath: String? = null) {
        markers.add(fqn)
        registrationsList += MarkerRegistration(fqn = fqn, sourceFilePath = sourceFilePath)
    }

    /**
     * marker class の FqN に紐づく per-marker option overrides を登録する。
     *
     * 同 marker に対して複数回呼ばれた場合は **後勝ち** (= 最後の登録値で上書き) する。
     * 通常 declaration checker は 1 つの class につき 1 回呼ばれるため、 競合は発生しない。
     *
     * 履歴 ([registrations]) には [registerMarker] と同様に毎回 entry が積まれる。
     *
     * @param fqn marker annotation class の完全修飾名
     * @param options 当該 marker の per-marker override
     * @param sourceFilePath register 発火元の declaration が含まれる source file path (任意、
     *   [registerMarker] と同じ semantics)
     */
    public fun registerMarkerOptions(
        fqn: String,
        options: CaptureCodeMarkerOptions,
        sourceFilePath: String? = null,
    ) {
        markers.add(fqn)
        markerOptionsTable[fqn] = options
        registrationsList += MarkerRegistration(fqn = fqn, sourceFilePath = sourceFilePath)
    }

    /**
     * marker FqN に対応する per-marker option overrides を返す。
     *
     * marker 自体が未登録の場合、 もしくは marker は登録済みだが option が未登録の場合は
     * [CaptureCodeMarkerOptions.DEFAULT] (= すべて `Override.Default`) を返す。 これにより
     * 既存の引数なし `@CaptureCode` marker は global config の値をそのまま使う。
     */
    public fun markerOptionsFor(fqn: String): CaptureCodeMarkerOptions =
        markerOptionsTable[fqn] ?: CaptureCodeMarkerOptions.DEFAULT

    /**
     * 与えられた FqN が登録済みの marker かどうかを返す。
     *
     * IR phase で annotation の type 経由で marker 判定を行う際の hot path。
     */
    public fun isMarker(fqn: String): Boolean = fqn in markers

    /**
     * task-127: 同 compilation 内で 2 回以上 register された FQN のリスト。
     *
     * 戻り値は **追加順 (= 最初に重複が観測された順)** の deterministic ordering。
     * caller の `WarnIfDuplicateMarkerFqn` は本リストを iterate して warning を発火する。
     *
     * 例えば commonMain で `com.example.Foo` を register、 jvmMain で同じ FQN を別 declaration
     * として register した場合、 戻り値は `listOf("com.example.Foo")` になる。 同 declaration を
     * 2 回 register したケース (= 通常ありえない pathological case) も同様に検出される。
     */
    public fun duplicateMarkerFqns(): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (registration in registrationsList) {
            counts[registration.fqn] = (counts[registration.fqn] ?: 0) + 1
        }
        return counts.entries.filter { it.value >= 2 }.map { it.key }
    }

    /**
     * task-127: 指定 FQN の registration 履歴を返す (= duplicate warning の location 情報用)。
     *
     * 同 FQN を複数回 register した場合、 各 entry がここに積まれているので caller は
     * 「最初の registration の file path」 を warning location として使える。
     */
    public fun registrationsFor(fqn: String): List<MarkerRegistration> =
        registrationsList.filter { it.fqn == fqn }

    /**
     * registry を空にする。テストおよび `IrGenerationExtension.generate` 完了時に呼ぶ。
     *
     * 1 つの ClassLoader で複数コンパイル (例: kctfork で連続 compile) を行う場合、
     * 前回コンパイルの marker FqN が次回に漏れないようにするために必要。
     */
    public fun reset() {
        markers.clear()
        registrationsList.clear()
        markerOptionsTable.clear()
    }

    /**
     * task-127: 1 回の `registerMarker` / `registerMarkerOptions` 呼び出しを表すデータクラス。
     *
     * @property fqn 登録された marker annotation class の完全修飾名
     * @property sourceFilePath register 発火元の declaration source file path (取得不能なら null)
     */
    public data class MarkerRegistration(
        val fqn: String,
        val sourceFilePath: String?,
    )
}
