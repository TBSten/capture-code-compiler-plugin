package me.tbsten.capture.code.compat

/**
 * compiler plugin がキャプチャした 1 件分のサイト情報を表す共通モデル。
 *
 * compat-kXXXX 各バージョンの IR 走査結果はこの型に集約され、後段の書き換え phase
 * (`capturedSources<T>()` の置換) で参照される。
 *
 * Phase 1 では marker FqN と source 文字列のみを保持する最小モデル。
 * Phase 2 で `SourceLocation` / ユーザ定義パラメータの保持を追加し、`kind` の網羅も増やす想定。
 *
 * @property markerFqn キャプチャ対象に付与された marker annotation の完全修飾名
 *                     (例: `me.tbsten.capture.code.testapp.Snippets_Case1`)
 * @property source 抽出されたソースコード文字列 (annotation 行は除外、dedent は未実施)
 * @property kind サイトの種別。Phase 1 では [CaptureKind.PROPERTY] のみサポート
 */
public data class CapturedSite(
    val markerFqn: String,
    val source: String,
    val kind: CaptureKind = CaptureKind.PROPERTY,
) {
    /**
     * キャプチャ対象の declaration 種別。
     * Phase 1 では [PROPERTY] のみ。Phase 2 で class / object / function / typealias / file /
     * expression を追加する。
     */
    public enum class CaptureKind {
        PROPERTY,
    }
}
