package io.github.uharaqo.epoque.integration.epoque.project

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.serialization.JsonCodecFactory

sealed interface Project {
  data object Empty : Project

  data object Default : Project
}

private val builder = Epoque.journalFor<ProjectEvent>(JsonCodecFactory())

val PROJECT_JOURNAL = builder.summaryFor<Project>(Project.Empty) {
  eventHandlerFor<ProjectCreated> { s, e ->
    require(s is Project.Empty) { "Project already exists" }
    Project.Default
  }
}

val PROJECT_COMMANDS = builder.with(PROJECT_JOURNAL).routerFor<ProjectCommand> {
  commandHandlerFor<CreateProject, String>(
    { c -> "PREPARED" },
  ) { c, s, x ->
    println("Prepared Param: $x")
    if (s is Project.Empty) {
      chain("Foo", CreateTask("TaskX"))
      notify {
        println("NOTIFICATION SENT")
      }

      emit(c.toProjectCreated())
    } else {
      reject("Project already exists")
    }
  }
}
