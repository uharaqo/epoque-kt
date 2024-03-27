package io.github.uharaqo.epoque.api

import arrow.core.left
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED

fun interface EventHandler<S, E> {
  fun handle(s: S, e: E): S
}

@JvmInline
value class EventHandlerRegistry<S, E>(
  val registry: Map<EventType, EventHandler<S, E>>,
) {
  fun find(key: EventType): Failable<EventHandler<S, E>> =
    registry[key]?.right() ?: EVENT_NOT_SUPPORTED.toException(message = key.toString()).left()
}

interface EventHandlerExecutor<S> {
  val emptySummary: S

  fun computeNextSummary(
    prevSummary: S,
    eventType: EventType,
    event: SerializedEvent,
  ): Failable<S>
}
