package io.github.uharaqo.epoque.integration.epoque.task

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.serialization.JsonCodecFactory

sealed interface Task {
  data class Default(val started: Boolean) : Task
}

private val builder = Epoque.journalFor<TaskEvent>(JsonCodecFactory())

val TASK_JOURNAL = builder.summaryFor<Task?>(null) {
  eventHandlerFor<TaskCreated> { s, e ->
    if (s == null) Task.Default(false) else error("Already created")
  }
  eventHandlerFor<TaskStarted> { s, e ->
    requireNotNull(s)
    Task.Default(true)
  }
  eventHandlerFor<TaskEnded> { s, e ->
    requireNotNull(s)
    Task.Default(false)
  }
}

val TASK_COMMANDS = builder.with(TASK_JOURNAL).routerFor<TaskCommand> {
  commandHandlerFor<CreateTask> { c, s ->
    if (s != null) reject("Already created")
    if (c.project != null && !exists(PROJECT_JOURNAL, c.project)) reject("Project Not Found")

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
