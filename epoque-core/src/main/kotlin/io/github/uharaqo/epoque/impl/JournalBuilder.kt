package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.codecFor

interface JournalBuilder<S, E : Any> {

  fun eventHandlerFor(
    codec: DataCodec<E>,
    handler: EventHandler<S, E>,
  ): JournalBuilder<S, E>

  fun build(): Journal<S, E>
}

class DefaultJournalBuilder<S, E : Any>(
  val journalGroupId: JournalGroupId,
  val emptySummary: S,
  val codecFactory: DataCodecFactory,
) : JournalBuilder<S, E> {
  private val eventCodecRegistryBuilder = EventCodecRegistryBuilder<E>(codecFactory)
  private val eventHandlerRegistryBuilder = EventHandlerRegistryBuilder<S, E>()

  /** [CE]: Concrete type of the event */
  inline fun <reified CE : E> JournalBuilder<S, E>.eventHandlerFor(
    noinline eventHandler: (s: S, e: CE) -> S,
  ): JournalBuilder<S, E> =
    @Suppress("UNCHECKED_CAST")
    eventHandlerFor(
      codec = codecFactory.codecFor<CE>() as DataCodec<E>,
      handler = EventHandler { s: S, e: CE -> eventHandler(s, e) } as EventHandler<S, E>,
    )

  override fun eventHandlerFor(
    codec: DataCodec<E>,
    handler: EventHandler<S, E>,
  ): DefaultJournalBuilder<S, E> = this.also {
    eventCodecRegistryBuilder.register(codec)
    eventHandlerRegistryBuilder.register(EventType.of(codec.type), handler)
  }

  override fun build(): Journal<S, E> =
    Journal(
      journalGroupId,
      emptySummary,
      eventHandlerRegistryBuilder.build(),
      eventCodecRegistryBuilder.build(),
    )
}

class EventHandlerRegistryBuilder<S, E : Any> {
  private val registry = RegistryBuilder<EventType, EventHandler<S, E>>()

  /** [CE]: Concrete type of the event */
  inline fun <reified CE : E> register(handler: EventHandler<S, CE>): EventHandlerRegistryBuilder<S, E> =
    this.also {
      @Suppress("UNCHECKED_CAST")
      register(EventType.of<CE>(), handler as EventHandler<S, E>)
    }

  fun register(type: EventType, handler: EventHandler<S, E>): EventHandlerRegistryBuilder<S, E> =
    this.also {
      registry[type] = handler
    }

  fun build(): EventHandlerRegistry<S, E> = EventHandlerRegistry(
    registry.build { EVENT_NOT_SUPPORTED.toException(message = it.toString()) },
  )
}

class DefaultEventHandlerExecutor<S>(
  val journal: Journal<S, *>,
) : EventHandlerExecutor<S> by journal

fun <S> Journal<S, *>.toEventHandlerExecutor(): EventHandlerExecutor<S> =
  DefaultEventHandlerExecutor(this)
