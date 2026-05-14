plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // task-037: publish convention plugin が vanniktech maven-publish の DSL
    // (`mavenPublishing { ... }`) を参照するため、 classpath に追加する。
    implementation(libs.maven.publish.plugin)
}
