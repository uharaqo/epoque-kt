import java.net.URI

plugins {
  `maven-publish`
  signing
}

tasks.withType(Jar::class.java) {
  manifest {
    attributes(
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

@Managed
interface SonatypePublishExtension {
  val userName: Property<String>
  val repoName: Property<String>
  val artifactName: Property<String>
  val artifactDesc: Property<String>
}

val extensions = project.extensions.create<SonatypePublishExtension>("sonatypePublish")

publishing {
  repositories {
    maven {
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
  }

  publications.create<MavenPublication>("maven") {
    val ext = the<SonatypePublishExtension>()

    val httpUrl =
      ext.userName.zip(ext.repoName) { user, repo -> "https://github.com/$user/$repo" }
    val gitUrl =
      ext.userName.zip(ext.repoName) { user, repo -> "scm:git://github.com/$user/$repo.git" }

    from(components["java"])

    pom {
      name = ext.artifactName
      description = ext.artifactDesc
      url = httpUrl
      scm {
        url = httpUrl
        connection = gitUrl
        developerConnection = gitUrl
      }
      developers {
        developer {
          id = ext.userName
          name = ext.userName
        }
      }
      licenses {
        license {
          name = "The Apache License, Version 2.0"
          url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        }
      }
    }
  }
}

signing {
  // provided by env var: ORG_GRADLE_PROJECT_signingKey
  val signingKey = findProperty("signingKey") as String?
  // provided by env var: ORG_GRADLE_PROJECT_signingPassword
  val signingPassword = findProperty("signingPassword") as String?
  useInMemoryPgpKeys(signingKey, signingPassword)

  sign(publishing.publications)
}
