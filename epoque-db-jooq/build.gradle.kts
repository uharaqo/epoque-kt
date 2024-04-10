import Sonatype_publish_gradle.Publish.toSonatype

plugins {
  id("kotlin-conventions")
}

dependencies {
  api(project(":epoque-core"))
  api(libs.jooq.kotlin)
  api(libs.jooq.kotlin.coroutines)

  // test
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.arrow)
  testImplementation(libs.mockk)
  testImplementation(libs.logback.classic)
  testImplementation(libs.h2)
}

toSonatype(
  userName = "uharaqo",
  repoName = "epoque-kt",
  artifactName = project.name,
  artifactDesc = "jOOQ plugin for epoque-kt",
)
