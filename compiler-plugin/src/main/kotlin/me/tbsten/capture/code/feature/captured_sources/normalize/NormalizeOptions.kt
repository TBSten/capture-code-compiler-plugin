package me.tbsten.capture.code.feature.captured_sources.normalize

/**
 * `SourceNormalizer.normalize` の動作を切り替えるオプション群。
 *
 * すべてのオプションは独立に on/off でき、組み合わせて評価される。
 * design §5 Logic D / §5 Logic I の DSL options に対応する。
 *
 * @property dedent 全行の最小インデント幅 (空白行を除く) を計算し、各行から削除する。
 * @property trimBlankEdges 先頭/末尾の空白行を drop する。中間の空白行は維持される。
 * @property stripPackageAndImport file 起源の正規化で `package` / `import` 宣言行を除外する。
 *                                  declaration 起源では原則 false を指定する。
 * @property stripLeadingAnnotationLines declaration 起源で、normalize 後の先頭に残った
 *                                       `@Marker` annotation 行を除外する。IR offset には
 *                                       既に annotation 行が含まれない前提でデフォルトは false。
 */
internal data class NormalizeOptions(
    val dedent: Boolean = true,
    val trimBlankEdges: Boolean = true,
    val stripPackageAndImport: Boolean = false,
    val stripLeadingAnnotationLines: Boolean = false,
) {
    companion object {
        /** declaration 起源 (property / class / function / typealias / object) のデフォルト。 */
        val DECLARATION_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = false,
            stripLeadingAnnotationLines = false,
        )

        /** file 起源 (`@file:Marker`) のデフォルト。design 仕様によりデフォルトで package/import を除外する。 */
        val FILE_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = true,
            stripLeadingAnnotationLines = false,
        )

        /** 式起源 (`@Marker (expr)`)。dedent + blank trim のみ。 */
        val EXPRESSION_DEFAULT: NormalizeOptions = NormalizeOptions(
            dedent = true,
            trimBlankEdges = true,
            stripPackageAndImport = false,
            stripLeadingAnnotationLines = false,
        )
    }
}
