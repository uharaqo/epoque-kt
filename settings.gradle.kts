plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "epoque-kt"

include(
  "epoque-core",
  "epoque-db-jooq",
  "epoque-db-jooq-mysql",
  "epoque-test",
  "integration-test",
)
