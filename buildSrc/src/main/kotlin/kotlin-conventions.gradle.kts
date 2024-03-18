plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("org.jlleitschuh.gradle.ktlint")
  id("org.jlleitschuh.gradle.ktlint-idea")
}

repositories {
  mavenCentral()
  mavenLocal()
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
  google()
  gradlePluginPortal()
}

group = "io.github.uharaqo"
version = "0.0.1"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  filter {
    isFailOnNoMatchingTests = false
  }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
  verbose.set(true)
  android.set(false)
  outputToConsole.set(true)
  coloredOutput.set(true)
  outputColorName.set("RED")
//    debug.set(true)
//    ignoreFailures.set(true)
  filter {
    exclude { it.file.path.contains("$buildDir/generated/") }
  }
}
