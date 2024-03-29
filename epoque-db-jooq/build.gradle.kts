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
