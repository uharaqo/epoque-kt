plugins {
  id("kotlin-conventions")
  id("sonatype-publish")
}

dependencies {
  api(project(":epoque-core"))
  api(project(":epoque-db-jooq"))
  api(libs.jooq.kotlin)
  api(libs.jooq.kotlin.coroutines)
  api(libs.h2)
  implementation(libs.slf4j)

  // test
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.arrow)
  testImplementation(libs.mockk)
  testImplementation(libs.logback.classic)
}

sonatypePublish {
  userName = "uharaqo"
  repoName = "epoque-kt"
  artifactName = project.name
  artifactDesc = "Testing support for epoque-kt"
}
