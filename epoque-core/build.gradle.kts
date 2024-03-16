plugins {
  id("kotlin-conventions")
  // this serialization plugin is only required for testing
  kotlin("plugin.serialization") version libs.versions.kotlin.asProvider().get()
}

dependencies {
  api(libs.arrow.core)
  api(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.core)

  // test
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotest.assertions.arrow)
  testImplementation(libs.mockk)
}
