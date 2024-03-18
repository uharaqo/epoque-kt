package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.EventHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent
import io.github.uharaqo.epoque.impl.Registry

interface EventHandler<S, E> {
  fun handle(summary: S, event: E): Either<EventHandlerFailure, S>
}

@JvmInline
value class EventHandlerRegistry<S, E>(
  private val registry: Registry<EventType, EventHandler<S, E>, UnexpectedEvent>,
) : Registry<EventType, EventHandler<S, E>, UnexpectedEvent> by registry

interface EventHandlerExecutor<S> {
  val emptySummary: S

  fun computeNextSummary(
    prevSummary: S,
    eventType: EventType,
    event: SerializedEvent,
  ): Either<SummaryAggregationFailure, S>
}
