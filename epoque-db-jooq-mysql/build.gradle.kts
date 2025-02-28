plugins {
  id("kotlin-conventions")
  id("sonatype-publish")
}

dependencies {
  api(project(":epoque-db-jooq"))
  api(libs.jooq.kotlin)
  api(libs.jooq.kotlin.coroutines)
  api(libs.mysql)

  // test
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.arrow)
  testImplementation(libs.mockk)
  testImplementation(libs.logback.classic)
  testImplementation(libs.h2)
}

sonatypePublish {
  userName = "uharaqo"
  repoName = "epoque-kt"
  artifactName = project.name
  artifactDesc = "MySQL plugin for epoque-kt"
}
