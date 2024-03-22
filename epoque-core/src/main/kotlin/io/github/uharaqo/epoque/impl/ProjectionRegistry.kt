package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_PROJECTION_FAILURE
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
    try {
      val tx = checkNotNull(TransactionContext.Key.get()) { "Transaction not found" }

      output.events.forEach { ve ->
        try {
          val p = projectionRegistry[ve.type] ?: return@forEach
          val event = p.eventCodec.decoder.decode(ve.event).getOrElse { throw it }

          val projectionEvent = ProjectionEvent(output.context.key, ve.version, event)

          @Suppress("UNCHECKED_CAST")
          (p as Projection<Any?>).process(projectionEvent, tx)
        } catch (e: Exception) {
          throw EVENT_PROJECTION_FAILURE.toException(e, ve.type.toString())
        }
      }
    } catch (e: InterruptedException) {
      throw e.also { Thread.interrupted() }
    } catch (t: Throwable) {
      throw EVENT_PROJECTION_FAILURE.toException(t)
    }
  }
}
