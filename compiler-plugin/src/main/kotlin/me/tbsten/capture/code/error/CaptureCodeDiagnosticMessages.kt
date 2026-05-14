package me.tbsten.capture.code.error

/**
 * Capture Code plugin が発する全診断メッセージの **bilingual (英語 + 日本語) SSOT**。
 *
 * `CaptureCodeDiagnostics.kt` / `CapturedSourcesCheckerDiagnostics.kt` の renderer 文面を
 * このファイルに集約している。renderer は本 object の [BilingualMessage] を
 * [CaptureCodeMessageLocale] に応じて render するだけになる。
 *
 * ## 文面ポリシー
 *
 * - **英語**: 命令形 + `.` で 1 文 + `Suggested fix:` 1-2 行 (該当する診断のみ)。FQN は呼び出し側で
 *   `MessageFormat` の `''{0}''` に埋め込む。
 * - **日本語**: です・ます調 1 文 + `修正方法:` 1-2 行。技術用語 (annotation / `internal` / Retention 等)
 *   はそのまま英語で残し、ローカライズによる曖昧化を避ける。
 *
 * ## メッセージ ID 命名規則 (`CC_<feature>_<rule>`)
 *
 * - `CC_MARKER_*` — `@CaptureCode` メタ付き marker annotation の制約違反 (Logic F)
 * - `CC_CAPTUREDSOURCES_*` — `capturedSources<T>()` 呼び出しの制約違反 (Logic G)
 *
 * 既存 test は **文面の部分一致** (`shouldContain`) で検証しているため、英語側の
 * 既存 phrase (`must be 'internal' or 'private'` 等) は **後方互換のため温存** する。
 *
 * design `compiler-plugin-design.md` §5 Logic F / G、§8.5 (`error/` SSOT) 参照。
 */
internal object CaptureCodeDiagnosticMessages {

    // ------------------------------------------------------------------
    // Logic F (ID: CC_MARKER_*): marker annotation の制約違反
    // ------------------------------------------------------------------

    /** `CC_MARKER_VISIBILITY_VIOLATION` — visibility が `internal` / `private` でない */
    val MARKER_VISIBILITY_VIOLATION: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker annotation must be 'internal' or 'private'. " +
            "Cross-module capture is not supported in v1.\n" +
            "Suggested fix: change the visibility modifier to 'internal' or 'private'.",
        ja = "@CaptureCode marker annotation は 'internal' または 'private' で宣言する必要があります。" +
            "v1 ではモジュール跨ぎのキャプチャはサポートしていません。\n" +
            "修正方法: visibility modifier を 'internal' または 'private' に変更してください。",
    )

    /** `CC_MARKER_RETENTION_VIOLATION` — `@Retention` が `SOURCE` ではない (default `RUNTIME` 含む) */
    val MARKER_RETENTION_VIOLATION: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker annotation must use @Retention(AnnotationRetention.SOURCE).\n" +
            "Suggested fix: add or change to '@Retention(AnnotationRetention.SOURCE)' on the marker.",
        ja = "@CaptureCode marker annotation には @Retention(AnnotationRetention.SOURCE) が必要です。" +
            "未指定は default RUNTIME 扱いとなり許可されません。\n" +
            "修正方法: marker に '@Retention(AnnotationRetention.SOURCE)' を追加してください。",
    )

    /** `CC_MARKER_TARGET_EMPTY` — `@Target(...)` 未指定 / 空 */
    val MARKER_TARGET_EMPTY: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker annotation must specify at least one @Target site " +
            "(e.g., AnnotationTarget.PROPERTY).\n" +
            "Suggested fix: add '@Target(AnnotationTarget.PROPERTY, ...)' with one or more sites.",
        ja = "@CaptureCode marker annotation には少なくとも 1 つの @Target site を指定する必要があります " +
            "(例: AnnotationTarget.PROPERTY)。\n" +
            "修正方法: marker に '@Target(AnnotationTarget.PROPERTY, ...)' を追加し、" +
            "対象 site を 1 つ以上指定してください。",
    )

    /**
     * `CC_MARKER_PARAMETER_TYPE_INVALID` — parameter 型が Kotlin annotation 制約外
     *
     * 引数: `{0}` = parameter name。
     */
    val MARKER_PARAMETER_TYPE_INVALID: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker annotation parameter ''{0}'' has an unsupported type. " +
            "Kotlin annotation parameter types are limited to primitives, String, KClass, " +
            "enum, annotation, or arrays of these.\n" +
            "Suggested fix: change parameter ''{0}'' to one of the allowed annotation types " +
            "(e.g., String, Int, an enum class, or another annotation).",
        ja = "@CaptureCode marker annotation の parameter ''{0}'' の型はサポート対象外です。" +
            "Kotlin annotation の parameter 型は primitives / String / KClass / enum / annotation " +
            "またはそれらの配列に限られます。\n" +
            "修正方法: parameter ''{0}'' の型を、許可された annotation 型 " +
            "(例: String, Int, enum class, または別の annotation) に変更してください。",
    )

    /**
     * `CC_MARKER_FILLER_REQUIRES_DEFAULT` — filler 型 parameter (Source / SourceLocation /
     * CaptureKind) にデフォルト値がない
     *
     * 引数: `{0}` = parameter name。
     */
    val MARKER_FILLER_REQUIRES_DEFAULT: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker filler parameter ''{0}'' must have a default value " +
            "(e.g., 'val source: Source = Source()'). The plugin auto-fills filler values " +
            "at compile time, so use sites do not specify them explicitly.\n" +
            "Suggested fix: assign a default constructor call (e.g., '= Source()') to ''{0}''.",
        ja = "@CaptureCode marker の filler parameter ''{0}'' にはデフォルト値が必要です " +
            "(例: 'val source: Source = Source()')。filler 値は plugin がコンパイル時に自動で埋めるため、" +
            "use site では明示しません。\n" +
            "修正方法: parameter ''{0}'' にデフォルト constructor 呼び出し (例: '= Source()') を指定してください。",
    )

    /** `CC_MARKER_IS_EXPECT` — marker 自体が `expect` 宣言 */
    val MARKER_IS_EXPECT: BilingualMessage = BilingualMessage(
        en = "@CaptureCode marker annotation cannot be declared as 'expect'. " +
            "Markers must be concrete annotation declarations (see design §7.6).\n" +
            "Suggested fix: remove the 'expect' modifier; declare the marker concretely in commonMain.",
        ja = "@CaptureCode marker annotation は 'expect' 宣言にできません。" +
            "marker は具体的な annotation 宣言として記述してください (design §7.6 参照)。\n" +
            "修正方法: 'expect' modifier を削除し、commonMain に通常の annotation として宣言してください。",
    )

    // ------------------------------------------------------------------
    // Logic G (ID: CC_CAPTUREDSOURCES_*): capturedSources<T>() 呼び出しの制約違反
    // ------------------------------------------------------------------

    /**
     * `CC_CAPTUREDSOURCES_T_NOT_CAPTURE_CODE` — `T` が `@CaptureCode` 付き marker ではない
     *
     * 引数: `{0}` = T の FQN。
     */
    val CAPTUREDSOURCES_T_NOT_CAPTURE_CODE: BilingualMessage = BilingualMessage(
        en = "Type parameter T of capturedSources<T>() must be annotated with @CaptureCode. " +
            "{0} does not have @CaptureCode.\n" +
            "Suggested fix: add '@CaptureCode' meta-annotation to {0}, " +
            "or pass a @CaptureCode-meta marker as T.",
        ja = "capturedSources<T>() の型パラメータ T には @CaptureCode meta-annotation が必要です。" +
            "{0} には @CaptureCode が付いていません。\n" +
            "修正方法: {0} に '@CaptureCode' meta-annotation を追加するか、" +
            "@CaptureCode 付き marker を T に渡してください。",
    )

    // ------------------------------------------------------------------
    // Locale rendering
    // ------------------------------------------------------------------

    /**
     * 現在の JVM 環境 (`CAPTURECODE_LOCALE`) に従って、 [BilingualMessage] を rendering 用
     * format string に変換する。
     *
     * `RootDiagnosticRendererFactory` への登録時に 1 度だけ呼ばれる想定。
     * 環境変数を JVM 起動後に動的に変更しても反映されない (Kotlin compiler の long-lived
     * daemon process で実用上問題にならない)。
     */
    fun render(message: BilingualMessage): String =
        message.render(CaptureCodeMessageLocale.fromEnv())
}
