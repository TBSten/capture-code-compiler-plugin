package me.tbsten.capture.code

/**
 * Marker meta-annotation. Mark your own annotation class with `@CaptureCode`
 * to make it a Capture Code marker.
 *
 * Each parameter ([includeKdoc] / [includeImports] / [includeAnnotationLines] /
 * [dedent] / [includeLineInfo]) lets a marker override the corresponding
 * Gradle plugin DSL option (`captureCode { ... }`) on a **per-marker** basis.
 *
 * Resolution priority:
 *
 * 1. If the parameter is set to [Override.Yes] / [Override.No], the marker-level
 *    value wins.
 * 2. Otherwise ([Override.Default]) the Gradle DSL config value is used.
 * 3. If no Gradle DSL config was provided either, the library built-in default
 *    is used.
 *
 * Existing marker declarations that pass no arguments (`@CaptureCode`) keep the
 * pre-existing global behaviour, since every parameter defaults to
 * [Override.Default].
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class CaptureCode(
    val includeKdoc: Override = Override.Default,
    val includeImports: Override = Override.Default,
    val includeAnnotationLines: Override = Override.Default,
    val dedent: Override = Override.Default,
    val includeLineInfo: Override = Override.Default,
) {
    /**
     * Tri-state override flag.
     *
     * Booleans cannot express "no override" because every boolean value has
     * to choose `true` or `false`. The compiler plugin needs to distinguish
     * "the marker explicitly opted in/out" from "the marker did not say
     * anything" so it can fall through to the Gradle DSL config — hence the
     * dedicated enum.
     */
    public enum class Override {
        /** Use the Gradle plugin config (or the library built-in default). */
        Default,

        /** Force the option to `true` for this marker. */
        Yes,

        /** Force the option to `false` for this marker. */
        No,
    }
}

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
