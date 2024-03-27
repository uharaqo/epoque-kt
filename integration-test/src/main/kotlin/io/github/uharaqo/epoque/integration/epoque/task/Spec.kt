package io.github.uharaqo.epoque.integration.epoque.task

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.dsl.MapSummaryCache
import io.github.uharaqo.epoque.dsl.journalFor
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL

// summary with multiple subtypes
sealed interface Task {
  data object Empty : Task
  data class Default(val started: Boolean) : Task
}

val TASK_JOURNAL = Epoque.journalFor<TaskCommand, Task, TaskEvent>(JsonCodecFactory()) {
  summaryCache = MapSummaryCache()

  commands {
    defaultWriteOption = WriteOption.JOURNAL_LOCK

    onCommand<CreateTask> {
      handle { c, s ->
        if (s !is Task.Empty) reject("Already created")
        if (c.project != null && !exists(PROJECT_JOURNAL, c.project)) reject("Project Not Found")

        emit(c.toTaskCreated())
      }
    }
    onCommand<StartTask> {
      handle { c, s ->
        if (s !is Task.Default) reject("Not found")
        if (!s.started) emit(c.toTaskStarted())
      }
    }
    onCommand<EndTask> {
      handle { c, s ->
        if (s !is Task.Default) reject("Not found")
        if (s.started) emit(c.toTaskEnded())
      }
    }
  }

  events(Task.Empty) {
    onEvent<TaskCreated> {
      handle { s, e ->
        check(s is Task.Empty) { "Already created" }
        Task.Default(false)
      }
    }
    onEvent<TaskStarted> {
      handle { s, e ->
        check(s !is Task.Empty)
        Task.Default(true)
      }
    }
    onEvent<TaskEnded> {
      handle { s, e ->
        check(s !is Task.Empty)
        Task.Default(false)
      }
    }
  }
}
