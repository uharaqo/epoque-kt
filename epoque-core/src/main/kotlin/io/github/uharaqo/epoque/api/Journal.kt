package io.github.uharaqo.epoque.api

class Journal<S, E : Any>(
  val journalGroupId: JournalGroupId,
  override val emptySummary: S,
  override val eventHandlerRegistry: EventHandlerRegistry<S, E>,
  override val eventCodecRegistry: EventCodecRegistry,
) : CanComputeNextSummary<S, E> {
  override fun toString(): String = "Journal for $journalGroupId"
}
