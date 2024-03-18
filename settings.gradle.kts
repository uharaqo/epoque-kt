plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "epoque-kt"

include(
  "epoque-core",
  "epoque-db-jooq",
)
