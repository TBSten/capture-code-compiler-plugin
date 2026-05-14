package me.tbsten.capture.code.gradle

/**
 * `gradle-plugin` 内に閉じた minimal な Kotlin version comparator。
 *
 * `:compiler-plugin:compat` の [me.tbsten.capture.code.compat.KotlinToolingVersion] と
 * 同等の責務だが、 **gradle-plugin の compileClasspath に compat module を引き込まない** ため
 * 独立に実装した。 SSOT 違反ではない (gradle-plugin 側は dispatch を行わず単純な
 * "<" / ">=" 比較しかしないため、 compat 側の maturity-aware comparison は不要)。
 *
 * 受け付ける形式 (`KotlinVersionParts.parse` の挙動):
 * - `"2.0.0"`, `"2.0.21"`, `"2.1.0"` → major=2, minor=N, patch=N, hasPreReleaseClassifier=false
 * - `"2.1.0-Beta2"`, `"2.1.0-RC1"`, `"2.1.0-dev-7791"` → ベースは数値、 classifier 部分は
 *   pre-release マーカーとして保持
 *
 * 比較ロジック (`KotlinVersionParts.compareTo`):
 * - major / minor / patch を数値比較
 * - 同じ base なら pre-release classifier 付きの方が **小さい** (= 未満) と扱う
 *   (例: `"2.1.0-Beta2" < "2.1.0"`)
 *
 * これにより、 `MIN_SUPPORTED_VERSION = "2.0.0"` という設定値だけで pre-release の
 * `2.0.0-RC1` を fail させずに済む (一般ユーザは pre-release を使わないが、 比較ロジックの
 * 一貫性のため明示的にハンドリングする)。
 */
internal data class KotlinVersionParts(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Pre-release / dev / snapshot classifier (e.g. "Beta2", "RC1", "dev-7791")。 stable では `null`. */
    val preReleaseClassifier: String?,
) : Comparable<KotlinVersionParts> {

    override fun compareTo(other: KotlinVersionParts): Int {
        (major - other.major).takeIf { it != 0 }?.let { return it }
        (minor - other.minor).takeIf { it != 0 }?.let { return it }
        (patch - other.patch).takeIf { it != 0 }?.let { return it }
        // 同じ base: classifier 無し (stable) > classifier 有り (pre-release)
        return when {
            preReleaseClassifier == null && other.preReleaseClassifier == null -> 0
            preReleaseClassifier == null -> 1
            other.preReleaseClassifier == null -> -1
            else -> preReleaseClassifier.compareTo(other.preReleaseClassifier)
        }
    }

    companion object {

        /**
         * `"2.0.0"` / `"2.1.0-Beta2"` / `"2.1.0-dev-7791"` を [KotlinVersionParts] に parse する。
         *
         * 未知の形式 (例えば `"snapshot"` のような non-numeric ベース) は `null` を返し、
         * 呼び出し側は version guard を skip して安全側に倒す。
         */
        fun parse(versionString: String): KotlinVersionParts? {
            val base = versionString.substringBefore('-')
            val classifier = if ('-' in versionString) versionString.substringAfter('-') else null
            val parts = base.split('.')
            if (parts.size != 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            return KotlinVersionParts(major, minor, patch, classifier)
        }
    }
}
