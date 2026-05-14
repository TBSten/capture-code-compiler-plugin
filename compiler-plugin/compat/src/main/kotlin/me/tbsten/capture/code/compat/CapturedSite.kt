package me.tbsten.capture.code.compat

/**
 * compiler plugin がキャプチャした 1 件分のサイト情報を表す共通モデル。
 *
 * compat-kXXXX 各バージョンの IR 走査結果はこの型に集約され、後段の書き換え phase
 * (`capturedSources<T>()` の置換) で参照される。
 *
 * `kind` enum は宣言レベル 5 種 (PROPERTY / CLASS / OBJECT / FUNCTION / TYPEALIAS) + ファイル
 * (FILE) + 式 (EXPRESSION) の合計 7 種で、Logic B-ir / B-fir が走査結果をこの kind 単位で
 * 集約する。
 *
 * location 系フィールド (`packageFqn` / `filePath` / `startLine` / `endLine`) は `SourceLocation`
 * filler の自動値埋めに使われる。後方互換のためデフォルト値を持つ。
 *
 * @property markerFqn キャプチャ対象に付与された marker annotation の完全修飾名
 *                     (例: `me.tbsten.capture.code.testapp.Snippets_Case1`)
 * @property source 抽出されたソースコード文字列 (annotation 行は除外、dedent は未実施)
 * @property kind サイトの種別 ([CaptureKind] のいずれか)
 * @property packageFqn declaration を内包する Kotlin package の FqN (例: `me.tbsten.capture.code.testapp`)。
 *                      `SourceLocation.packageName` filler に入る値。
 * @property filePath declaration を内包する file の path (実装上は `IrFile.fileEntry.name`)。
 *                    `SourceLocation.filePath` filler に入る値。絶対パスになりやすいので最終的には
 *                    Gradle module root 相対化が望ましいが、現状はこのまま使用する。
 * @property startLine declaration の開始行 (1 始まり)。`SourceLocation.startLine` filler に入る値。
 * @property endLine declaration の終了行 (1 始まり、最後の `}` の行)。`SourceLocation.endLine` filler
 *                   に入る値。
 */
public data class CapturedSite(
    val markerFqn: String,
    val source: String,
    val kind: CaptureKind = CaptureKind.PROPERTY,
    val packageFqn: String = "",
    val filePath: String = "",
    val startLine: Int = 0,
    val endLine: Int = 0,
) {
    /**
     * キャプチャ対象の declaration 種別。
     *
     * design §3.1 の `CaptureKind.Kind` (runtime filler 型の enum) と一対一対応する。
     * Logic H (filler 自動値埋め) で本 enum を `CaptureKind.Kind` の値に変換して
     * marker constructor に注入する。
     *
     * - [PROPERTY] — `val` / `var` を含む property 宣言
     * - [CLASS] — class / interface / annotation class / data class 等
     *   (`IrClass.kind = CLASS / INTERFACE / ANNOTATION_CLASS` の総称)
     * - [OBJECT] — `object` 宣言・companion object
     *   (`IrClass.kind = OBJECT`)
     * - [FUNCTION] — top-level / member function
     *   (`IrSimpleFunction`、property accessor を除く)
     * - [TYPEALIAS] — `typealias` 宣言 (`IrTypeAlias`)
     * - [FILE] — `@file:Marker` でファイル全体をキャプチャしたサイト
     *   (`IrFile.annotations` 起源)。`SourceLocation.startLine` は常に 1、`endLine` は
     *   ファイル末尾の行 (`IrFileEntry.lineCount`) を入れる。
     * - [EXPRESSION] — `@Marker (expr)` で式に annotation を付けたサイト
     *   (FIR session storage 起源)。Kotlin 2.0 では IR phase で式 annotation が残らないため、
     *   FIR phase で offset を session storage に push → IR phase で読み出す bridge 経路で
     *   site を構築する (`compiler-plugin-design.md` §5 B-fir 参照)。
     */
    public enum class CaptureKind {
        PROPERTY,
        CLASS,
        OBJECT,
        FUNCTION,
        TYPEALIAS,
        FILE,
        EXPRESSION,
    }
}
