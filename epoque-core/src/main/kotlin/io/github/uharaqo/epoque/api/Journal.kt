package io.github.uharaqo.epoque.api

import io.github.uharaqo.epoque.dsl.CommandHandlerSetup

data class Journal<C, S, E>(
  val journalGroupId: JournalGroupId,
  val summaryType: SummaryType,
  override val emptySummary: S,
  val summaryCache: SummaryCache?,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventHandlerRegistry: EventHandlerRegistry<S, E>,
  val commandHandlers: Map<CommandType, CommandHandlerSetup<C, S, E>>,
  val commandCodecRegistry: CommandCodecRegistry,
) : CanComputeNextSummary<S, E>, CanAggregateEvents<S> {

  override fun toString(): String = "Journal for $journalGroupId"
}

fun Journal<*, *, *>.keyFor(id: String): JournalKey =
  JournalKey(journalGroupId, JournalId(id))
