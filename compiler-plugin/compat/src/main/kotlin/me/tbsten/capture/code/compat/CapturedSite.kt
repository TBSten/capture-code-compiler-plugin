package me.tbsten.capture.code.compat

/**
 * compiler plugin がキャプチャした 1 件分のサイト情報を表す共通モデル。
 *
 * compat-kXXXX 各バージョンの IR 走査結果はこの型に集約され、後段の書き換え phase
 * (`capturedSources<T>()` の置換) で参照される。
 *
 * task-005 (Phase 1) では marker FqN と source 文字列、`kind=PROPERTY` のみを保持する最小モデルだった。
 * task-012 (Phase 2 Wave 2 Track β 起点) で `kind` enum を 5 種類 (PROPERTY / CLASS / OBJECT /
 * FUNCTION / TYPEALIAS) に拡張し、Logic B-ir が宣言レベル 5 種全部を走査できるようになった。
 * EXPRESSION / FILE は task-016 / task-017 で追加予定。
 *
 * Phase 2 では更に `SourceLocation` / ユーザ定義パラメータの保持を追加する想定 (task-013/014)。
 *
 * @property markerFqn キャプチャ対象に付与された marker annotation の完全修飾名
 *                     (例: `me.tbsten.capture.code.testapp.Snippets_Case1`)
 * @property source 抽出されたソースコード文字列 (annotation 行は除外、dedent は未実施)
 * @property kind サイトの種別。task-012 で `kind=OBJECT/CLASS/FUNCTION/TYPEALIAS` を追加
 */
public data class CapturedSite(
    val markerFqn: String,
    val source: String,
    val kind: CaptureKind = CaptureKind.PROPERTY,
) {
    /**
     * キャプチャ対象の declaration 種別。
     *
     * design §3.1 の `CaptureKind.Kind` (runtime filler 型の enum) と一対一対応する。
     * task-013 (Logic H に filler 自動値埋め) で本 enum を `CaptureKind.Kind` の値に変換して
     * marker constructor に注入する。
     *
     * - [PROPERTY] — task-005 で実装、`val` / `var` を含む property 宣言
     * - [CLASS] — task-012 で追加。class / interface / annotation class / data class 等
     *   (`IrClass.kind = CLASS / INTERFACE / ANNOTATION_CLASS` の総称)
     * - [OBJECT] — task-012 で追加。`object` 宣言・companion object
     *   (`IrClass.kind = OBJECT`)
     * - [FUNCTION] — task-012 で追加。top-level / member function
     *   (`IrSimpleFunction`、property accessor を除く)
     * - [TYPEALIAS] — task-012 で追加。`typealias` 宣言 (`IrTypeAlias`)
     *
     * EXPRESSION (task-017) / FILE (task-016) は後続で追加する。
     */
    public enum class CaptureKind {
        PROPERTY,
        CLASS,
        OBJECT,
        FUNCTION,
        TYPEALIAS,
    }
}
