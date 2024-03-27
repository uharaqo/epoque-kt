package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventType

class EventHandlersBuilder<S, E : Any> : EventHandlersDsl<S, E>() {
  private val handlers = mutableMapOf<EventType, EventHandlerBuilder<S, E>>()

  override fun <CE : E> onEvent(
    type: EventType,
    block: EventHandlerDsl<S, CE>.() -> Unit,
  ) {
    @Suppress("UNCHECKED_CAST")
    handlers += type to EventHandlerBuilder<S, CE>(type).apply(block) as EventHandlerBuilder<S, E>
  }

  fun build(codecFactory: DataCodecFactory): Map<EventType, EventHandlerSetup<S, E>> =
    handlers.mapValues { it.value.build(codecFactory) }
}

class EventHandlerBuilder<S, E : Any>(private val type: EventType) : EventHandlerDsl<S, E>() {
  private var handler: EventHandler<S, E>? = null

  override fun handle(eventHandler: EventHandler<S, E>) {
    handler.shouldBeNull("events.onEvent.handle")
    handler = eventHandler
  }

  fun build(
    codecFactory: DataCodecFactory,
  ): EventHandlerSetup<S, E> {
    handler.shouldBeDefined("events.onEvent.handle")

    @Suppress("UNCHECKED_CAST")
    return EventHandlerSetup(
      codecFactory.create(type.unwrap).toEventCodec() as EventCodec<E>,
      handler!!,
    )
  }
}
