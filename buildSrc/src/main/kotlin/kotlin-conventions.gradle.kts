plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("jacoco")
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
  withSourcesJar()
  withJavadocJar()
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

jacoco {
  toolVersion = "0.8.11"
  reportsDirectory.set(project.layout.buildDirectory.dir("jacoco"))
}
tasks.jacocoTestReport {
  reports {
    csv.required.set(true)
    xml.required.set(true)
  }
}
