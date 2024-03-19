package io.github.uharaqo.epoque.api

import io.github.uharaqo.epoque.impl.Registry

interface EventHandler<S, E> {
  fun handle(summary: S, event: E): Failable<S>
}

@JvmInline
value class EventHandlerRegistry<S, E>(
  private val registry: Registry<EventType, EventHandler<S, E>>,
) : Registry<EventType, EventHandler<S, E>> by registry

interface EventHandlerExecutor<S> {
  val emptySummary: S

  fun computeNextSummary(
    prevSummary: S,
    eventType: EventType,
    event: SerializedEvent,
  ): Failable<S>
}
