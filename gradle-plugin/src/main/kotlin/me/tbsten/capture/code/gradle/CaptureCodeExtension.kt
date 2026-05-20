package me.tbsten.capture.code.gradle

/**
 * Capture Code Gradle plugin の DSL extension。
 *
 * ユーザは `build.gradle.kts` で次のように設定できる:
 *
 * ```kotlin
 * captureCode {
 *     includeImports = true
 *     dedent = false
 *     warnOnEmptyCapture = true
 * }
 * ```
 *
 * 6 つの option は `CaptureCodePluginConfig` (SSOT) に集約され、CommandLineProcessor を経由して
 * FIR / IR extension に渡される。design `compiler-plugin-design.md` §5 Logic I / §8.5 参照。
 *
 * @property includeKdoc キャプチャしたソースに KDoc コメントを残すか。
 *                       デフォルト `true` (`CaptureCodePluginConfig.DEFAULT.includeKdoc`)。
 * @property includeImports file 起源キャプチャで `import` 宣言行を含めるか。デフォルト `false`。
 * @property includeAnnotationLines 宣言先頭の `@Marker` annotation 行を含めるか。デフォルト `false`。
 * @property dedent 全行から共通の先頭インデントを取り除くか。デフォルト `true`。
 * @property includeLineInfo `SourceLocation.startLine` / `endLine` を実値で埋めるか。デフォルト `true`。
 * @property warnOnEmptyCapture `capturedSources<T>()` が同一 compilation 内で `@T` site を
 *                              1 つも見つけられなかった場合に `CC_CAPTUREDSOURCES_NO_MARKER_FOUND`
 *                              warning を発火するかどうか。 デフォルト `false` (opt-in)。
 *                              KMP / multi-module setup では別 compilation の site を見落とす
 *                              false positive が出るため、 single-module pure JVM project で
 *                              意図的に enable する想定。 task-120-B Phase 7 / task-123 で導入。
 */
public abstract class CaptureCodeExtension {
    public var includeKdoc: Boolean = true
    public var includeImports: Boolean = false
    public var includeAnnotationLines: Boolean = false
    public var dedent: Boolean = true
    public var includeLineInfo: Boolean = true
    public var warnOnEmptyCapture: Boolean = false

    public companion object {
        /** Gradle project に `extensions.create(EXTENSION_NAME, ...)` で登録する名前。 */
        public const val EXTENSION_NAME: String = "captureCode"
    }
}
