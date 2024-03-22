plugins {
  id("kotlin-conventions")
  id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
  kotlin("plugin.serialization") version libs.versions.kotlin.asProvider().get()
}

dependencies {
  api(project(":epoque-core"))
  api(project(":epoque-db-jooq"))
  api(libs.jooq.kotlin)
  api(libs.jooq.kotlin.coroutines)
  implementation("io.mcarle:konvert-api:3.0.1")
  ksp("io.mcarle:konvert:3.0.1")

  // test
  testImplementation(project(":epoque-test"))
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.arrow)
  testImplementation(libs.mockk)
  testImplementation(libs.logback.classic)
  testImplementation(libs.h2)
}
