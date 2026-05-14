rootProject.name = "Capture-Code-compiler-plugin"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}

include(":annotation")
include(":compiler-plugin")
include(":compiler-plugin:compat")
include(":compiler-plugin:compat-k200")
include(":compiler-plugin:compat-k210")
include(":gradle-plugin")
include(":integration-test:test-jvm")
include(":integration-test:test-kmp")
include(":integration-test:test-gradle-plugin")
