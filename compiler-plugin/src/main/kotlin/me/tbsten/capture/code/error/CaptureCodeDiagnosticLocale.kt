package me.tbsten.capture.code.error

/**
 * Capture Code plugin の診断メッセージのロケール切替ヘルパ。
 *
 * `RootDiagnosticRendererFactory` は Kotlin compiler プロセス内で **静的に 1 度だけ** 登録される
 * ため、本来の意味での「コンパイル単位ごとの locale 切替」は plugin 側からは制御しづらい
 * (Kotlin compiler の標準 i18n API が `KtDiagnosticFactory` レベルでは公開されていない)。
 *
 * そこで、本 plugin では:
 *
 * 1. **デフォルトは bilingual (英語 + 日本語) 併記** — IntelliJ IDEA / Android Studio
 *    の popup でも Gradle build window でも両言語が同時に読めるため、開発者が locale を
 *    意識せず常に理解しやすい
 * 2. JVM 環境変数 `CAPTURECODE_LOCALE` で表示モードを切替できる:
 *    - `en` → 英語のみ
 *    - `ja` → 日本語のみ
 *    - その他 / 未設定 → bilingual (default)
 *
 * 環境変数は JVM 起動時に 1 度だけ読まれる (Kotlin compiler の長寿命プロセス内で
 * 動的に切り替える必要は無い)。CI / IDE 連携で個別に上書きしたい場合のみ設定する。
 *
 * design `compiler-plugin-design.md` §5 Logic F / G 参照。
 */
internal enum class CaptureCodeMessageLocale {
    /** 英語と日本語を併記する (default)。 */
    BILINGUAL,

    /** 英語のみ。CI ログを英語に揃えたい場合などに利用。 */
    EN,

    /** 日本語のみ。国内チームで日本語化したい場合などに利用。 */
    JA,
    ;

    companion object {
        /**
         * 環境変数 `CAPTURECODE_LOCALE` を参照して [CaptureCodeMessageLocale] を決める。
         *
         * 値は case insensitive。不明値 / 未設定は [BILINGUAL] にフォールバックする。
         */
        fun fromEnv(env: (String) -> String? = System::getenv): CaptureCodeMessageLocale =
            when (env("CAPTURECODE_LOCALE")?.lowercase()) {
                "en", "english" -> EN
                "ja", "japanese", "jp" -> JA
                else -> BILINGUAL
            }
    }
}

/**
 * 英語 + 日本語の 2 言語メッセージペアを表す内部ヘルパ。
 *
 * `MessageFormat` の `''{0}''` 等の placeholder は両言語で同じ index を使う。
 * placeholder 名 (例: `'item'`) などは言語非依存にしているので、英訳 / 和訳どちらでも
 * `'item'` のままで通る。
 */
internal data class BilingualMessage(
    val en: String,
    val ja: String,
) {
    /**
     * [CaptureCodeMessageLocale] に従って rendering 用 message format string を返す。
     *
     * `BILINGUAL` モードでは英語 → `日本語:` 改行 → 日本語 の順で連結する。
     * IntelliJ の inspection popup / Gradle build window 双方で改行が保持される
     * (Kotlin compiler の reporter は改行をそのまま渡す)。
     */
    fun render(locale: CaptureCodeMessageLocale): String = when (locale) {
        CaptureCodeMessageLocale.EN -> en
        CaptureCodeMessageLocale.JA -> ja
        CaptureCodeMessageLocale.BILINGUAL -> "$en\n日本語: $ja"
    }
}
