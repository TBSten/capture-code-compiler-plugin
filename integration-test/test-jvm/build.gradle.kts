plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":annotation"))
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}

application {
    mainClass = "me.tbsten.capture.code.testapp.MainKt"
}
