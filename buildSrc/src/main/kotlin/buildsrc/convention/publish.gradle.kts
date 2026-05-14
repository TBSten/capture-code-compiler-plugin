package buildsrc.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension

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
