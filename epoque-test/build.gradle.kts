plugins {
  id("kotlin-conventions")
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
