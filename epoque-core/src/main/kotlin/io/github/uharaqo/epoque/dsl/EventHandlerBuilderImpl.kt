package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.impl.toEventCodec

class EventHandlersBuilder<S, E : Any>(
  val emptySummary: S,
  val defaultCacheOption: CacheOption,
) : EventHandlersDsl<S, E>() {
  private val handlers = mutableMapOf<EventType, EventHandlerBuilder<S, E>>()

  override fun <CE : E> onEvent(
    type: EventType,
    block: EventHandlerDsl<S, CE>.() -> Unit,
  ) {
    @Suppress("UNCHECKED_CAST")
    val eventHandlerBuilder =
      EventHandlerBuilder<S, CE>(type).apply(block) as EventHandlerBuilder<S, E>
    handlers += type to eventHandlerBuilder
  }

  fun build(codecFactory: DataCodecFactory): Pair<S, Map<EventType, EventHandlerEntry<S, E>>> =
    emptySummary to handlers.mapValues { it.value.build(defaultCacheOption, codecFactory) }
}

class EventHandlerBuilder<S, E : Any>(private val type: EventType) : EventHandlerDsl<S, E>() {
  private lateinit var handler: (S, E) -> S
  private var ignoreUnknownEvents = false // TODO
  private val cacheOptionBuilder = CacheOptionBuilder()

  override fun ignoreUnknownEvents() {
    this.ignoreUnknownEvents = true
  }

  override fun cache(block: CacheOptionDsl.() -> Unit) {
    cacheOptionBuilder.block()
  }

  override fun handle(block: (S, E) -> S) {
    handler = block
  }

  fun build(
    defaultCacheOption: CacheOption,
    codecFactory: DataCodecFactory,
  ): EventHandlerEntry<S, E> =
    @Suppress("UNCHECKED_CAST")
    EventHandlerEntry(
      ignoreUnknownEvents,
      codecFactory.create(type.unwrap).toEventCodec() as EventCodec<E>,
      cacheOptionBuilder.build(defaultCacheOption),
      handler,
    )
}

class CacheOptionBuilder : CacheOptionDsl {
  override fun build(defaultCacheOption: CacheOption): CacheOption =
    defaultCacheOption
}

fun <S, E> EventHandlerEntry<S, E>.toEventHandler(): EventHandler<S, E> =
  EventHandler { s, e -> handler(s, e) }
