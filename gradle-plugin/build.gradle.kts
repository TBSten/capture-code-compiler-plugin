plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    implementation(project(":compiler-plugin"))
    implementation(project(":annotation"))
}

gradlePlugin {
    plugins {
        create("captureCode") {
            id = "me.tbsten.capture.code"
            displayName = "Capture Code Compiler Plugin"
            description = "Kotlin compiler plugin for capturing source code"
            implementationClass = "me.tbsten.capture.code.gradle.CaptureCodeGradlePlugin"
        }
    }
}
