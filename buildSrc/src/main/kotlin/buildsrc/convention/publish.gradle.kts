package buildsrc.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension

// ----------------------------------------------------------------------------
// task-037: Maven Central 向け共通 publish 設定
//
// 適用方法 (各 publish 対象 module の build.gradle.kts):
//   plugins {
//       id("buildsrc.convention.publish")
//   }
//   // (vanniktech) coordinates / artifactId は module 側で個別に
//   mavenPublishing {
//       coordinates(group as String, "annotation", version as String)
//   }
//
// 本 convention が提供するもの:
//   - vanniktech `com.vanniktech.maven.publish` plugin の適用
//   - `publishToMavenCentral()` — `VERSION_NAME` の `-SNAPSHOT` 末尾で
//     `automaticRelease` を切替
//   - POM 共通 metadata (name は module 側で上書き、 url / license / developer
//     / scm を統一)
//   - signing 条件分岐: `signingInMemoryKey` (env 経由) が存在する時のみ
//     `signAllPublications()` を呼ぶ。 これにより `publishToMavenLocal` は
//     credentials なしで動く。
//
// 環境変数 / Gradle property の対応 (vanniktech v0.30 ベース):
//   - `ORG_GRADLE_PROJECT_mavenCentralUsername` / `mavenCentralPassword`
//   - `ORG_GRADLE_PROJECT_signingInMemoryKey` (ASCII-armored GPG private key)
//   - `ORG_GRADLE_PROJECT_signingInMemoryKeyId` (任意、 sub-key 指定)
//   - `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` (任意)
//
// 詳細手順: `docs/publishing.md`
// ----------------------------------------------------------------------------

plugins {
    id("com.vanniktech.maven.publish")
}

val isSnapshot = (project.version as String).endsWith("-SNAPSHOT")

extensions.configure<MavenPublishBaseExtension> {
    // SNAPSHOT は automaticRelease しない (Sonatype の SNAPSHOT repo は手動 close 不要)
    publishToMavenCentral(automaticRelease = !isSnapshot)

    // signing は credentials 揃った時のみ有効化 (publishToMavenLocal を阻害しない)
    val hasInMemoryKey = providers.gradleProperty("signingInMemoryKey").isPresent
    val hasKeyId = providers.gradleProperty("signing.keyId").isPresent
    if (hasInMemoryKey || hasKeyId) {
        signAllPublications()
    }

    pom {
        // name は module 側で上書きされる前提だが、 default 値として group:artifactId
        // を入れておく (Sonatype の必須項目を満たす保険)
        name.set(project.provider { "${project.group}:${project.name}" })
        description.set("Capture Code compiler plugin component (${project.name})")
        url.set("https://github.com/tbsten/capture-code-compiler-plugin")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("tbsten")
                name.set("tbsten")
                email.set("programmingcafeteria@gmail.com")
                url.set("https://github.com/tbsten")
            }
        }

        scm {
            url.set("https://github.com/tbsten/capture-code-compiler-plugin")
            connection.set("scm:git:https://github.com/tbsten/capture-code-compiler-plugin.git")
            developerConnection.set("scm:git:ssh://git@github.com/tbsten/capture-code-compiler-plugin.git")
        }
    }
}
