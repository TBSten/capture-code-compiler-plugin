// Kotlin Gradle plugin は buildSrc 経由で classpath に提供されるため、
// ここでは KGP 以外のサブプロジェクトプラグインのみを apply(false) で宣言する。
//
// task-037: `com.vanniktech.maven.publish` は buildSrc の `publish` convention plugin
// が classpath に乗せるため (`buildSrc/build.gradle.kts` 参照)、 ここで apply(false)
// 宣言すると "plugin already on classpath with unknown version" になるため
// 宣言しない。 各 publish 対象 module は `id("buildsrc.convention.publish")` 経由で
// 利用する。
plugins {
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.shadow).apply(false)
}

// ----------------------------------------------------------------------------
// task-040: 全 subproject の group を `me.tbsten.capture.code` に統一する。
//
// 理由: `:integration-test:test-gradle-plugin` の fixture project は本体を
// `includeBuild` 経由で取り込み、 `dependencySubstitution` で coordinate を
// project に置き換える。 Gradle の `includeBuild` は default で project の
// `group:archivesName` を coordinate として subst candidate にするため、
// `project.group` を publication coordinate (= `me.tbsten.capture.code`) に
// 合わせておかないと substitution が match しない (task-026 で踏んだ罠の真因)。
//
// なお `:annotation` の publish 時の coordinate は `mavenPublishing.coordinates(
// "me.tbsten.capture.code", "annotation", ...)` で同じ group に明示しているため、
// publish 動作には影響しない (SSOT を維持)。
// ----------------------------------------------------------------------------
allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}
