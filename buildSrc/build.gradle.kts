plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // publish convention plugin が vanniktech maven-publish の DSL
    // (`mavenPublishing { ... }`) を参照するため、 classpath に追加する。
    implementation(libs.maven.publish.plugin)
    // task-085: AGP も buildSrc に載せて、 `:annotation` (KMP + androidTarget)
    // 適用時に KGP が AGP を **同 classloader** から検出できるようにする。
    // この依存追加が無いと "Can't infer current AndroidGradlePluginVersion:
    // Is the Android plugin applied?" で失敗する。
    implementation(libs.android.gradle.plugin)
}
