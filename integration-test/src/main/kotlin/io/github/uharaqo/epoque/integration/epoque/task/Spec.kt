package io.github.uharaqo.epoque.integration.epoque.task

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.serialization.JsonCodecFactory

sealed interface Task {
  data object Empty : Task
  data class Default(val started: Boolean) : Task
}

private val builder = Epoque.journalFor<TaskEvent>(JsonCodecFactory())

val TASK_JOURNAL = builder.summaryFor<Task>(Task.Empty) {
  eventHandlerFor<TaskCreated> { s, e ->
    if (s is Task.Empty) Task.Default(false) else error("Already created")
  }
  eventHandlerFor<TaskStarted> { s, e -> Task.Default(true) }
  eventHandlerFor<TaskEnded> { s, e -> Task.Default(false) }
}

val TASK_COMMANDS = builder.routerFor<TaskCommand, _, _>(TASK_JOURNAL) {
  commandHandlerFor<CreateTask> { c, s ->
    if (s !is Task.Empty) reject("Already created")
    if (!exists(PROJECT_JOURNAL, c.project)) reject("Project Not Found")

    emit(c.toTaskCreated())
  }
  commandHandlerFor<StartTask> { c, s ->
    if (s !is Task.Default) reject("Not found")
    if (!s.started) emit(c.toTaskStarted())
  }
  commandHandlerFor<EndTask> { c, s ->
    if (s !is Task.Default) reject("Not found")
    if (s.started) emit(c.toTaskEnded())
  }
}
