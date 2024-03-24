package io.github.uharaqo.epoque.db.jooq

import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.Projection
import io.github.uharaqo.epoque.api.ProjectionEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.builder.ProjectionRegistry
import io.github.uharaqo.epoque.builder.RegistryBuilder

fun interface JooqProjection<E> {
  suspend fun JooqTransactionContext.process(event: ProjectionEvent<E>)
}

class DefaultJooqProjection<E>(
  override val eventCodec: EventCodec<E>,
  private val projection: JooqProjection<E>,
) : Projection<E> {

  override suspend fun process(event: ProjectionEvent<E>, tx: TransactionContext) {
    tx.asJooq { with(projection) { process(event) } }
  }
}

class JooqProjectionBuilder<S, E : Any>(private val journal: Journal<S, E>) {
  private val registryBuilder = RegistryBuilder<EventType, Projection<E>>()

  inline fun <reified CE : E> projectionFor(projection: JooqProjection<CE>) =
    @Suppress("UNCHECKED_CAST")
    projectionFor(EventType.of<CE>(), projection as JooqProjection<E>)

  fun projectionFor(eventType: EventType, projection: JooqProjection<E>) {
    val codec =
      journal.eventCodecRegistry.find<E>(eventType)
        .getOrElse { throw IllegalStateException("EventType not supported. type: $eventType, journalGroupId: ${journal.journalGroupId}") }
    registryBuilder[eventType] = DefaultJooqProjection(codec, projection)
  }

  fun build(): ProjectionRegistry = ProjectionRegistry(registryBuilder.buildIntoMap())
}
