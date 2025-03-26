package io.github.uharaqo.epoque.integration.epoque.task

import arrow.core.getOrElse
import arrow.core.raise.either
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.dsl.CommandHandlersDsl
import io.github.uharaqo.epoque.dsl.EventHandlersDsl
import io.github.uharaqo.epoque.dsl.MapSummaryCache
import io.github.uharaqo.epoque.dsl.journalFor
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL

// summary with multiple subtypes
sealed interface Task {
  data object Empty : Task
  data class Default(val started: Boolean) : Task
}

val TASK_JOURNAL = Epoque.journalFor<TaskCommand, Task, TaskEvent> {
  codecFactory = JsonCodecFactory()
  summaryCache = MapSummaryCache()

  commands {
    defaultWriteOption = WriteOption.JOURNAL_LOCK
    createTask()
    startTask()
    endTask()
    createAndStartTask()
  }

  events(Task.Empty) {
    taskCreated()
    taskStarted()
    taskEnded()
  }
}

private typealias COMMANDS = CommandHandlersDsl<TaskCommand, Task, TaskEvent>

private fun COMMANDS.createTask() =
  onCommand<CreateTask> {
    handle { c, s ->
      if (s !is Task.Empty) reject("Already created")
      if (c.project != null && !exists(PROJECT_JOURNAL, c.project)) reject("Project Not Found")

      emit(c.toTaskCreated())
    }
  }

private fun COMMANDS.startTask() =
  onCommand<StartTask> {
    handle { c, s ->
      if (s !is Task.Default) reject("Not found")
      if (!s.started) emit(c.toTaskStarted())
    }
  }

private fun COMMANDS.createAndStartTask() =
  onCommand<CreateAndStartTask> {
    writeOption = WriteOption.JOURNAL_LOCK

    handle { c, s ->
      val input = CommandInput.get()!!
      either {
        val codecs = CommandRouter.get()!!.commandCodecRegistry

        val type1 = CommandType.of<CreateTask>()
        codecs.find<CreateTask>(type1).bind()
          .encode(c.createTask).bind()
          .let { chain(input.copy(type = type1, payload = it)) }

        val type2 = CommandType.of<StartTask>()
        codecs.find<StartTask>(type2).bind()
          .encode(c.startTask).bind()
          .let { chain(input.copy(type = type2, payload = it)) }
      }.getOrElse { reject("ERROR", it) }
    }
  }

private fun COMMANDS.endTask() =
  onCommand<EndTask> {
    handle { c, s ->
      if (s !is Task.Default) reject("Not found")
      if (s.started) emit(c.toTaskEnded())
    }
  }

private typealias EVENTS = EventHandlersDsl<Task, TaskEvent>

private fun EVENTS.taskCreated() =
  onEvent<TaskCreated> {
    handle { s, e ->
      check(s is Task.Empty) { "Already created" }
      Task.Default(false)
    }
  }

private fun EVENTS.taskStarted() =
  onEvent<TaskStarted> {
    handle { s, e ->
      check(s !is Task.Empty)
      Task.Default(true)
    }
  }

private fun EVENTS.taskEnded() =
  onEvent<TaskEnded> {
    handle { s, e ->
      check(s !is Task.Empty)
      Task.Default(false)
    }
  }
