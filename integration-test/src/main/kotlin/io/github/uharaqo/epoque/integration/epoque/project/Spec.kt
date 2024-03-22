package io.github.uharaqo.epoque.integration.epoque.project

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.integration.Utils

sealed interface Project {
  data object Empty : Project

  data object Default : Project
}

private val builder = Epoque.journalFor<ProjectEvent>(Utils.codecFactory)

val PROJECT_JOURNAL = builder.summaryFor<Project>(Project.Empty) {
  eventHandlerFor<ProjectCreated> { s, e ->
    require(s is Project.Empty) { "Project already exists" }
    Project.Default
  }
}

val PROJECT_COMMANDS = builder.routerFor<ProjectCommand, Project, ProjectEvent>(PROJECT_JOURNAL) {
  commandHandlerFor<CreateProject> { c, s ->
    if (s is Project.Empty) {
      emit(c.toProjectCreated())
    } else {
      reject("Project already exists")
    }
  }
}
