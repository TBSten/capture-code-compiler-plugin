plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        mainRun {
            mainClass = "me.tbsten.capture.code.testapp.MainKt"
        }
    }
    js(IR) {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":annotation"))
        }
    }
}

dependencies {
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
