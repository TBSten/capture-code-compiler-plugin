package me.tbsten.capture.code

/**
 * Marker meta-annotation. Mark your own annotation class with `@CaptureCode`
 * to make it a Capture Code marker.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class CaptureCode

/** Filler type: source code text captured at compile time. */
public annotation class Source(val value: String = "")

/** Filler type: source location of the captured site. */
public annotation class SourceLocation(
    val packageName: String = "",
    val filePath: String = "",
    val startLine: Int = 0,
    val endLine: Int = 0,
)

/** Filler type: kind of the captured site. */
public annotation class CaptureKind(val value: Kind = Kind.UNKNOWN) {
    /** Category of the source-language element that was captured. */
    public enum class Kind {
        UNKNOWN, EXPRESSION, PROPERTY, CLASS, OBJECT, FUNCTION, TYPEALIAS, FILE,
    }
}

/**
 * Collect all sites in the current module marked with `T`.
 * The compiler plugin replaces this call with an inlined list literal at compile time.
 *
 * Calling this function without the compiler plugin applied results in an error.
 */
public inline fun <reified T : Annotation> capturedSources(): List<T> =
    error("CaptureCode compiler plugin is not applied")
