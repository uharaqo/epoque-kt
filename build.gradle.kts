plugins {
  id("kotlin-conventions")
  id("jacoco")
}

tasks.register<JacocoReport>("generateJacocoReport") {
  dependsOn(subprojects.map { it.tasks.getByName("jacocoTestReport") })

  group = "verification"
  description = "Generate combined Jacoco report for all modules"

  reports {
    csv.required.set(true)
    xml.required.set(true)
    html.required.set(true)
  }

  classDirectories.setFrom(
    files(
      subprojects.flatMap { it.the<SourceSetContainer>().getByName("main").output.classesDirs },
    ),
  )
  sourceDirectories.setFrom(
    files(
      subprojects.flatMap { it.the<SourceSetContainer>().getByName("main").allSource.srcDirs },
    ),
  )
  executionData.setFrom(
    files(
      subprojects.flatMap {
        it.tasks.getByName("jacocoTestReport", JacocoReport::class).executionData
      },
    ),
  )
}
