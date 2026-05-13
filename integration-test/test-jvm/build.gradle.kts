plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":annotation"))
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

application {
    mainClass = "me.tbsten.capture.code.testapp.MainKt"
}
