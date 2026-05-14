package me.tbsten.capture.code.compat

/**
 * Kotlin tooling version with maturity-aware comparison.
 *
 * Adapted from Metro's `KotlinToolingVersion`
 * (https://github.com/ZacSweers/metro, Apache 2.0) and compose-preview-lab's
 * port of the same. Trimmed to the subset required by
 * [CompatContext.Factory] selection.
 *
 * Examples:
 * - "2.0.0", "2.0.21", "2.1.0"
 * - "2.1.0-Beta2"
 * - "2.1.0-dev-2124"
 * - "2.0.0-RC", "2.0.0-RC2"
 *
 * 戦略 B (compat module 分離) を採用しているこの project では、 各
 * `compat-kXXX` モジュールが自身の `minVersion` を `KotlinToolingVersion` で
 * 表現し、 ServiceLoader 経由で current Kotlin に最も適合する Factory を選ぶ。
 * dev / Beta / RC のような pre-release track 比較を正しく行うために、 単純な
 * String 比較ではなくこの class を使う。
 */
public class KotlinToolingVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
    public val classifier: String?,
) : Comparable<KotlinToolingVersion> {

    /**
     * Pre-release track の成熟度。 比較順は ascending:
     * `SNAPSHOT < DEV < MILESTONE < ALPHA < BETA < RC < STABLE`。
     */
    public enum class Maturity { SNAPSHOT, DEV, MILESTONE, ALPHA, BETA, RC, STABLE }

    public val maturity: Maturity = run {
        val c = classifier?.lowercase()
        when {
            c == null || c.matches(Regex("""(release-)?\d+""")) -> Maturity.STABLE
            c == "snapshot" -> Maturity.SNAPSHOT
            c.matches(Regex("""rc\d*(-release)?(-?\d+)?""")) -> Maturity.RC
            c.matches(Regex("""beta\d*(-release)?(-?\d+)?""")) -> Maturity.BETA
            c.matches(Regex("""alpha\d*(-release)?(-?\d+)?""")) -> Maturity.ALPHA
            c.matches(Regex("""m\d+(-release)?(-\d+)?""")) -> Maturity.MILESTONE
            else -> Maturity.DEV
        }
    }

    /** `2.1.0-dev-2124` のような dev build か。 dev track は Beta/RC と特殊に扱う。 */
    public val isDev: Boolean get() = maturity == Maturity.DEV

    private val classifierNumber: Int? by lazy {
        classifier?.let {
            Regex("""(?:rc|beta|alpha|m)(\d+)""", RegexOption.IGNORE_CASE).find(it)
        }?.groupValues?.get(1)?.toIntOrNull()
    }

    private val buildNumber: Int? by lazy {
        // dev-2124 / RC-200 / Beta1-200 など、 末尾の -<number> を取る。
        classifier?.let { Regex("""-(\d+)$""").find(it) }?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 比較順: major → minor → patch → [Maturity] → classifier number → build number →
     * classifier string (tiebreaker)。
     *
     * Examples:
     * - `KotlinToolingVersion("2.1.0") > KotlinToolingVersion("2.1.0-Beta2")` — STABLE > BETA
     * - `KotlinToolingVersion("2.1.0-RC2") > KotlinToolingVersion("2.1.0-RC1")` — higher RC number
     * - `KotlinToolingVersion("2.1.0-dev-2124") > KotlinToolingVersion("2.1.0-dev-100")` — higher build
     * - `KotlinToolingVersion("2.1.0") > KotlinToolingVersion("2.1.0-release-1")` — null classifier > non-null
     */
    override fun compareTo(other: KotlinToolingVersion): Int {
        (this.major - other.major).takeIf { it != 0 }?.let { return it }
        (this.minor - other.minor).takeIf { it != 0 }?.let { return it }
        (this.patch - other.patch).takeIf { it != 0 }?.let { return it }
        (this.maturity.ordinal - other.maturity.ordinal).takeIf { it != 0 }?.let { return it }
        // For STABLE: a missing classifier ranks higher than "release-N".
        if (this.classifier == null && other.classifier != null) return 1
        if (this.classifier != null && other.classifier == null) return -1
        val a = classifierNumber ?: 0
        val b = other.classifierNumber ?: 0
        (a - b).takeIf { it != 0 }?.let { return it }
        val ab = buildNumber ?: 0
        val bb = other.buildNumber ?: 0
        (ab - bb).takeIf { it != 0 }?.let { return it }
        // Tiebreaker: compareTo == 0 implies equals.
        val ac = this.classifier?.lowercase()
        val bc = other.classifier?.lowercase()
        if (ac != bc) {
            return ac?.compareTo(bc ?: return 1) ?: -1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is KotlinToolingVersion && compareTo(other) == 0

    override fun hashCode(): Int {
        var r = major
        r = 31 * r + minor
        r = 31 * r + patch
        r = 31 * r + (classifier?.lowercase()?.hashCode() ?: 0)
        return r
    }

    override fun toString(): String =
        if (classifier != null) "$major.$minor.$patch-$classifier" else "$major.$minor.$patch"
}

/**
 * Parses a string of the form "2.0.0", "2.1.0-Beta2", "2.1.0-dev-2124".
 *
 * Unrecognized strings fall back to `0.0.0` (with a `stderr` warning) so that
 * Factory selection still makes progress on unexpected version strings instead
 * of throwing at plugin load time.
 */
@Suppress("FunctionName")
public fun KotlinToolingVersion(versionString: String): KotlinToolingVersion {
    val base = versionString.substringBefore('-')
    val classifier = if ('-' in versionString) versionString.substringAfter('-') else null
    val parts = base.split('.')
    val isValid = parts.size == 3 && parts.all { it.toIntOrNull() != null }
    if (!isValid) {
        System.err.println(
            "WARNING: [CaptureCode] Unrecognised Kotlin version string '$versionString'; " +
                "falling back to 0.0.0. Factory selection may be incorrect.",
        )
    }
    return KotlinToolingVersion(
        major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
        classifier = classifier,
    )
}
