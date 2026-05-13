plugins {
    kotlin("multiplatform")
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(17)

    jvm()
    js { browser(); nodejs() }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Publishing to Maven Central
// https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
    publishToMavenCentral()
    coordinates("me.tbsten.capture.code", "annotation", "0.1.0-SNAPSHOT")

    pom {
        name = "Capture Code annotation"
        description = "Runtime API declarations for Capture Code compiler plugin"
        url = "https://github.com/tbsten/Capture-Code-compiler-plugin"

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "tbsten"
                name = "tbsten"
                email = "programmingcafeteria@gmail.com"
            }
        }

        scm {
            url = "https://github.com/tbsten/Capture-Code-compiler-plugin"
        }
    }
    if (project.hasProperty("signing.keyId")) signAllPublications()
}
