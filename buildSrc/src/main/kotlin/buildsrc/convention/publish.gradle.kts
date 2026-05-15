package buildsrc.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.vanniktech.maven.publish")
}

extensions.configure<MavenPublishBaseExtension> {
    // **新 Sonatype Central Portal** (https://central.sonatype.com) を明示。
    // default の `SonatypeHost.DEFAULT` は legacy OSSRH (https://oss.sonatype.org)
    // で、 そちらに対して `getProfiles` を叩くと 402 Payment Required で fail
    // (legacy Nexus profile が無い)。 namespace 取得時に Central Portal を選んだ
    // 場合は必ず `CENTRAL_PORTAL` を渡すこと。
    //
    // 本プロジェクトは `-SNAPSHOT` 接尾辞を使わない方針 (docs/versioning.md
    // "Between releases" 参照)。 `automaticRelease = true` を常時、 release は
    // tag push trigger に限定する設計。
    publishToMavenCentral(host = SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

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
