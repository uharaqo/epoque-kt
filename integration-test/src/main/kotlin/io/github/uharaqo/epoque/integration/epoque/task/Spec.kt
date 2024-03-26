package io.github.uharaqo.epoque.integration.epoque.task

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.codec.KotlinxJsonCodecFactory
import io.github.uharaqo.epoque.impl2.forEvent
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL

// summary with multiple subtypes
sealed interface Task {
  data object Empty : Task
  data class Default(val started: Boolean) : Task
}

private val builder = Epoque.forEvent<TaskEvent>(KotlinxJsonCodecFactory())

val TASK_JOURNAL = builder.forSummary<Task>(Task.Empty) {
  eventHandlerFor<TaskCreated> { s, e ->
    check(s is Task.Empty) { "Already created" }
    Task.Default(false)
  }
  eventHandlerFor<TaskStarted> { s, e ->
    check(s !is Task.Empty)
    Task.Default(true)
  }
  eventHandlerFor<TaskEnded> { s, e ->
    check(s !is Task.Empty)
    Task.Default(false)
  }
}

val TASK_COMMANDS = builder.with(TASK_JOURNAL).forCommand<TaskCommand> {
  commandHandlerFor<CreateTask> { c, s ->
    if (s !is Task.Empty) reject("Already created")
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
