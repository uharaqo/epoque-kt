package io.github.uharaqo.epoque.integration.epoque.project

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask

// simple summary without any sub types
data object Project

private val builder = Epoque.journalFor<ProjectEvent>(JsonCodecFactory())

val PROJECT_JOURNAL = builder.summaryFor<Project?>(null) {
  eventHandlerFor<ProjectCreated> { s, e ->
    check(s == null) { "Project already exists" }
    Project
  }
}

val PROJECT_COMMANDS = builder.with(PROJECT_JOURNAL).routerFor<ProjectCommand> {
  commandHandlerFor<CreateProject, String>(
    { c -> "PREPARED" },
  ) { c, s, x ->
    println("Prepared Param: $x")
    if (s != null) reject("Project already exists")

    chain("Foo", CreateTask("TaskX"))
    notify {
      println("NOTIFICATION SENT")
    }

    emit(c.toProjectCreated())
  }
}
