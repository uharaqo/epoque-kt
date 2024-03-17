package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.EventHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure

interface EventHandler<S, E> {
  fun handle(summary: S, event: E): Either<EventHandlerFailure, S>
}

interface SummaryGenerator<S> {
  val emptySummary: S

  fun generateSummary(prevSummary: S, event: SerializedEvent): Either<SummaryAggregationFailure, S>
}
