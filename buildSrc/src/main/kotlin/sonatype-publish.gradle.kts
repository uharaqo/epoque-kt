import java.net.URI

object Publish {
  fun Project.toSonatype(
    userName: String,
    repoName: String,
    artifactName: String,
    artifactDesc: String,
  ) {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    tasks.withType(Jar::class.java) {
      manifest {
        attributes(
          "Implementation-Title" to project.name,
          "Implementation-Version" to project.version,
        )
      }
    }

    configure<PublishingExtension> {
      val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

      repositories.maven {
        name = "OSSRH"
        url = URI.create(
          if (isReleaseVersion)
            "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
          else
            "https://s01.oss.sonatype.org/content/repositories/snapshots/",
        )
        credentials {
          username = System.getenv("MAVEN_USERNAME")
          password = System.getenv("MAVEN_PASSWORD")
        }
      }

      publications.withType(MavenPublication::class.java) {
        val httpUrl = "https://github.com/${userName}/${repoName}"
        val gitUrl = "scm:git://github.com/${userName}/${repoName}.git"

        from(components["java"])
        artifactId = repoName

        pom {
          name = artifactName
          description = artifactDesc
          url = httpUrl
          scm {
            url = httpUrl
            connection = gitUrl
            developerConnection = gitUrl
          }
          licenses {
            license {
              name = "The Apache License, Version 2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
          }
          developers {
            developer {
              id = userName
              name = userName
            }
          }
        }
      }

      configure<SigningExtension> {
        // provided by env var: ORG_GRADLE_PROJECT_signingKey
        val signingKey = findProperty("signingKey") as String?
        // provided by env var: ORG_GRADLE_PROJECT_signingPassword
        val signingPassword = findProperty("signingPassword") as String?
        useInMemoryPgpKeys(signingKey, signingPassword)

        sign(publications)
      }
    }
  }
}
