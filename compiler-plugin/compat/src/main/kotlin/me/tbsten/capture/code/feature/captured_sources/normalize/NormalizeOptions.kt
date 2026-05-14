package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * `SourceNormalizer.normalize` の動作を切り替えるオプション群。
 *
 * すべてのオプションは独立に on/off でき、組み合わせて評価される。
 * design §5 Logic D / §5 Logic I の DSL options に対応する。
 *
 * task-018 で導入された [me.tbsten.capture.code.CaptureCodePluginConfig] (compiler-plugin main module)
 * から起源別の [NormalizeOptions] へ投影する glue は task-013 の compat-k2000 wire up で
 * `K200CapturedSourcesRewriter.toNormalizeOptions` 相当として実装されている。
 *
 * @property dedent 全行の最小インデント幅 (空白行を除く) を計算し、各行から削除する。
 * @property trimBlankEdges 先頭/末尾の空白行を drop する。中間の空白行は維持される。
 * @property stripPackageAndImport file 起源の正規化で `package` / `import` 宣言行を除外する。
 *                                  declaration 起源では原則 false を指定する。
 * @property stripLeadingAnnotationLines declaration 起源で、normalize 後の先頭に残った
 *                                       `@Marker` annotation 行を除外する。IR offset には
 *                                       既に annotation 行が含まれない前提でデフォルトは false。
 * @property stripKdoc declaration / file 起源で、normalize 入力に含まれた leading KDoc
 *                     (`/** ... */`) 行群を除外する safety net。 task-042 で導入。 通常は
 *                     Logic C 側 ([findKDocExtendedStartOffset]) が `includeKdoc = false`
 *                     のときには KDoc を含めないため no-op だが、 万一含まれてしまった場合の
 *                     保険として動作する。 デフォルト false。
 */
public data class NormalizeOptions(
    val dedent: Boolean = true,
    val trimBlankEdges: Boolean = true,
    val stripPackageAndImport: Boolean = false,
    val stripLeadingAnnotationLines: Boolean = false,
    val stripKdoc: Boolean = false,
) {
    public companion object {
        /** declaration 起源 (property / class / function / typealias / object) のデフォルト。 */
        public val DECLARATION_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = false,
            stripLeadingAnnotationLines = false,
            stripKdoc = false,
        )

        /** file 起源 (`@file:Marker`) のデフォルト。design 仕様によりデフォルトで package/import を除外する。 */
        public val FILE_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = true,
            stripLeadingAnnotationLines = false,
            stripKdoc = false,
        )

        /** 式起源 (`@Marker (expr)`)。dedent + blank trim のみ。 */
        public val EXPRESSION_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = false,
            stripLeadingAnnotationLines = false,
            stripKdoc = false,
        )
    }
}
