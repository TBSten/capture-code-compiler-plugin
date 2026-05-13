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
 * }
 * ```
 *
 * 5 つの option は `CaptureCodePluginConfig` (SSOT) に集約され、CommandLineProcessor を経由して
 * FIR / IR extension に渡される。design `compiler-plugin-design.md` §5 Logic I / §8.5 参照。
 *
 * @property includeKdoc キャプチャしたソースに KDoc コメントを残すか。
 *                       デフォルト `true` (`CaptureCodePluginConfig.DEFAULT.includeKdoc`)。
 * @property includeImports file 起源キャプチャで `import` 宣言行を含めるか。デフォルト `false`。
 * @property includeAnnotationLines 宣言先頭の `@Marker` annotation 行を含めるか。デフォルト `false`。
 * @property dedent 全行から共通の先頭インデントを取り除くか。デフォルト `true`。
 * @property includeLineInfo `SourceLocation.startLine` / `endLine` を実値で埋めるか。デフォルト `true`。
 */
public abstract class CaptureCodeExtension {
    public var includeKdoc: Boolean = true
    public var includeImports: Boolean = false
    public var includeAnnotationLines: Boolean = false
    public var dedent: Boolean = true
    public var includeLineInfo: Boolean = true

    public companion object {
        /** Gradle project に `extensions.create(EXTENSION_NAME, ...)` で登録する名前。 */
        public const val EXTENSION_NAME: String = "captureCode"
    }
}
