dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        // task-085: AGP は Google Maven にしかないため google() を追加。
        // 既存 root settings.gradle.kts と同じ content filter で content-based
        // routing を有効化し、 mavenCentral との重複問い合わせを避ける。
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"
