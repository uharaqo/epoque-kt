package io.github.uharaqo.epoque.db.jooq

import arrow.core.getOrElse
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.Projection
import io.github.uharaqo.epoque.api.ProjectionEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.dsl.ProjectionRegistry

fun <E : Any> Epoque.projectionFor(
  journal: Journal<*, *, E>,
  block: JooqProjectionBuilder<E>.() -> Unit,
): ProjectionRegistry = Epoque.projectionFor(journal.eventCodecRegistry, block)

fun <E : Any> Epoque.projectionFor(
  eventCodecRegistry: EventCodecRegistry,
  block: JooqProjectionBuilder<E>.() -> Unit,
): ProjectionRegistry =
  JooqProjectionBuilder<E>(eventCodecRegistry).apply(block).build()

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

class JooqProjectionBuilder<E : Any>(private val eventCodecRegistry: EventCodecRegistry) {
  private val projections = mutableMapOf<EventType, Projection<E>>()

  inline fun <reified CE : E> projectionFor(projection: JooqProjection<CE>) =
    @Suppress("UNCHECKED_CAST")
    projectionFor(EventType.of<CE>(), projection as JooqProjection<E>)

  fun projectionFor(eventType: EventType, projection: JooqProjection<E>) {
    val codec = eventCodecRegistry.find<E>(eventType).getOrElse { throw it }
    projections[eventType] = DefaultJooqProjection(codec, projection)
  }

  fun build(): ProjectionRegistry = ProjectionRegistry(projections)
}
