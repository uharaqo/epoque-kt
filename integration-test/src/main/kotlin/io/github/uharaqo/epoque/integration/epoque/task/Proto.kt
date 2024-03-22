package io.github.uharaqo.epoque.integration.epoque.task

import io.mcarle.konvert.api.KonvertTo
import kotlinx.serialization.Serializable

@Serializable
sealed interface TaskCommand

@Serializable
sealed interface TaskEvent

@Serializable
@KonvertTo(TaskCreated::class)
data class CreateTask(
  val name: String,
  val startedAt: Long = System.currentTimeMillis(),
  val project: String? = null,
  val tags: Set<String> = emptySet(),
) : TaskCommand

@Serializable
data class TaskCreated(val name: String, val startedAt: Long, val project: String?, val tags: Set<String>) : TaskEvent

@Serializable
@KonvertTo(TaskStarted::class)
data class StartTask(val startedAt: Long = System.currentTimeMillis()) : TaskCommand

@Serializable
data class TaskStarted(val startedAt: Long) : TaskEvent

@Serializable
@KonvertTo(TaskEnded::class)
data class EndTask(val endedAt: Long = System.currentTimeMillis()) : TaskCommand

@Serializable
data class TaskEnded(val endedAt: Long) : TaskEvent
