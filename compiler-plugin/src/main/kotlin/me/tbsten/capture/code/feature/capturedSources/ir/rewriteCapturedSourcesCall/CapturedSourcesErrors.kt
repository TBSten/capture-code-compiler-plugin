package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall

import me.tbsten.capture.code.error.CaptureCodeCompilerPluginError

/**
 * Diagnostic ID + message + reply SSoT for Logic H ([RewriteCapturedSourcesCall]) — IR phase
 * 複数版 (`capturedSources<T>()`) で発生する compile error の文面 SSoT。 IR phase 限定のため、
 * 実発火は `MessageCollector.report(ERROR, ...)` 経由 (DiagnosticReporter が使えないため)。
 * 単数版の error SSoT は
 * [me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourceCall.CapturedSourceCallErrors]。
 *
 * **English-only** (task-122). `MessageFormat` の `'` literal escape 仕様により、 メッセージ中の
 * lone `'` はすべて `''` に二重化していること (commit `d5031c5` 参照)。
 */
public object CapturedSourcesErrors {

    /**
     * `CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED` — type argument T が `@CaptureCode`
     * meta-annotated (= marker のはず) なのに、 その declaration が今回の compilation unit に
     * 含まれておらず marker registry に登録されていない時に発火する compile error (bug-001)。
     *
     * silent skip すると rewrite されない runtime stub が class file に残り、 実行時に
     * `IllegalStateException("CaptureCode compiler plugin is not applied")` になる。 典型原因は
     * stale な incremental build (変更 file だけが compiler に渡されて marker declaration が
     * 落ちた round) か、 marker を別 module / 別 compilation に置いた構成。
     * Argument `{0}` は marker class FQN。
     */
    public val MARKER_NOT_REGISTERED: CaptureCodeCompilerPluginError = object : CaptureCodeCompilerPluginError {
        override val id: String = "CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED"
        override val message: String =
            "Marker class ''{0}'' is annotated with @CaptureCode, but its declaration is not part of " +
                "this compilation unit, so capturedSources<{0}>() cannot be rewritten and would fail at runtime.\n" +
                "Typical causes: a stale incremental build that passed only a subset of source files to " +
                "the compiler, or a marker declared in another module / compilation.\n" +
                "Suggested fix: run a clean build (e.g. ./gradlew clean), or move the marker, its use sites " +
                "and the capturedSources<...>() call into the same compilation."
        override val reply: String? =
            "Run a clean build, or move the marker, its use sites and the capturedSources<...>() call " +
                "into the same compilation."
    }
}
