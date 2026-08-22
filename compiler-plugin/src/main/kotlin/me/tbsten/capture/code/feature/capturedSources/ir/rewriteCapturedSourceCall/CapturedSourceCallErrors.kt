package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic H' ([RewriteCapturedSourceCall]) — IR phase
 * 単数版 (`capturedSource<T>()`) で発生する compile error の文面 SSoT。 IR phase 限定のため、
 * 実発火は `MessageCollector.report(ERROR, ...)` 経由 (DiagnosticReporter が使えないため)。
 *
 * **English-only** (task-122). `MessageFormat` の `'` literal escape 仕様により、 メッセージ中の
 * lone `'` はすべて `''` に二重化していること (commit `d5031c5` 参照)。
 */
public object CapturedSourceCallErrors {

    /**
     * `CC_CAPTUREDSOURCE_NO_SITE` — 単数版で 0 件サイトを検知した時に発火する compile error。
     * Argument `{0}` は marker class FQN。
     */
    public val NO_SITE: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCE_NO_SITE"
        override val message: String =
            "No site found for ''@{0}'' in this compilation. " +
                "capturedSource<T>() requires exactly one declaration marked with @{0}.\n" +
                "Suggested fix: add ''@{0}'' to exactly one declaration, " +
                "or switch to capturedSources<T>() if zero matches are acceptable."
        override val reply: String? =
            "Add the marker annotation to exactly one declaration, " +
                "or switch to capturedSources<T>() to allow zero matches."
    }

    /**
     * `CC_CAPTUREDSOURCE_MULTIPLE_SITES` — 単数版で 2 件以上のサイトを検知した時に発火する compile error。
     * Argument `{0}` は marker class FQN、 `{1}` は発見した全 site location の `file:line` カンマ区切り。
     */
    public val MULTIPLE_SITES: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCE_MULTIPLE_SITES"
        override val message: String =
            "Multiple sites found for ''@{0}'': {1}. " +
                "capturedSource<T>() requires exactly one declaration marked with @{0}.\n" +
                "Suggested fix: remove the excess marker annotations so exactly one site remains, " +
                "or switch to capturedSources<T>() to collect all."
        override val reply: String? =
            "Remove the excess marker annotations so exactly one site remains, " +
                "or switch to capturedSources<T>() to collect all."
    }

    /**
     * `CC_CAPTUREDSOURCE_MARKER_NOT_REGISTERED` — type argument T が `@CaptureCode`
     * meta-annotated (= marker のはず) なのに、 その declaration が今回の compilation unit に
     * 含まれておらず marker registry に登録されていない時に発火する compile error (bug-001)。
     * 複数版の
     * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.CapturedSourcesErrors.MARKER_NOT_REGISTERED]
     * と対称 (文面は capturedSource<T>() 用)。
     * Argument `{0}` は marker class FQN。
     */
    public val MARKER_NOT_REGISTERED: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCE_MARKER_NOT_REGISTERED"
        override val message: String =
            "Marker class ''{0}'' is annotated with @CaptureCode, but its declaration is not part of " +
                "this compilation unit, so capturedSource<{0}>() cannot be rewritten and would fail at runtime.\n" +
                "Typical causes: a stale incremental build that passed only a subset of source files to " +
                "the compiler, or a marker declared in another module / compilation.\n" +
                "Suggested fix: run a clean build (e.g. ./gradlew clean), or move the marker, its use site " +
                "and the capturedSource<...>() call into the same compilation."
        override val reply: String? =
            "Run a clean build, or move the marker, its use site and the capturedSource<...>() call " +
                "into the same compilation."
    }
}
