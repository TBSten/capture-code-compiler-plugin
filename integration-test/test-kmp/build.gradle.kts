plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        mainRun {
            mainClass = "me.tbsten.capture.code.testapp.MainKt"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
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

        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.assertions.core)
        }
    }
}

dependencies {
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
