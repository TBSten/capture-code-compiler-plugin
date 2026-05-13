// Kotlin Gradle plugin は buildSrc 経由で classpath に提供されるため、
// ここでは KGP 以外のサブプロジェクトプラグインのみを apply(false) で宣言する。
plugins {
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.shadow).apply(false)
}
