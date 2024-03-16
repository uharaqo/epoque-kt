package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure

interface SummaryGenerator<S> {
  val empty: S

  fun generate(prevSummary: S, event: SerializedEvent): Either<SummaryAggregationFailure, S>
}
