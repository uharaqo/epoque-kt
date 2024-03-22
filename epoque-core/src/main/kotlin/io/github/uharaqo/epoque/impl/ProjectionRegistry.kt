package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_PROJECTION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.UNKNOWN_EXCEPTION
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Projection
import io.github.uharaqo.epoque.api.ProjectionEvent
import io.github.uharaqo.epoque.api.TransactionContext

class ProjectionRegistry(
  private val registry: Map<EventType, Projection<*>>,
) {
  operator fun get(key: EventType): Projection<*>? = registry[key]

  operator fun plus(other: ProjectionRegistry) =
    ProjectionRegistry(registry + other.registry)

  fun toCallbackHandler(): CallbackHandler = TransactionalProjectionExecutor(this)

  companion object {
    fun from(registries: Iterable<ProjectionRegistry>): ProjectionRegistry =
      registries.reduce { acc, v -> acc + v }
  }
}

class TransactionalProjectionExecutor(
  private val projectionRegistry: ProjectionRegistry,
) : CallbackHandler {
  override suspend fun beforeCommit(output: CommandOutput) {
    val tx = TransactionContext.Key.get()
      ?: throw UNKNOWN_EXCEPTION.toException(message = "Transaction not found")

    output.events.forEach { ve ->
      val processor = projectionRegistry[ve.type] ?: return@forEach
      val event = processor.eventCodec.decoder.decode(ve.event).getOrElse { throw it }
      val projectionEvent = ProjectionEvent(output.context.key, ve.version, event)

      try {
        @Suppress("UNCHECKED_CAST")
        (processor as Projection<Any?>).process(projectionEvent, tx)
      } catch (e: Exception) {
        throw EVENT_PROJECTION_FAILURE.toException(e, ve.type.toString())
      }
    }
  }
}
