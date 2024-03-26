package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.WriteOption

class JournalBuilder<C : Any, S, E : Any>(
  val journalGroupId: JournalGroupId,
) : JournalDsl<C, S, E> {
  private lateinit var commandHandlersBuilder: CommandHandlersBuilder<C, S, E>
  private lateinit var eventHandlersBuilder: EventHandlersBuilder<S, E>

  override fun commands(
    defaultWriteOption: WriteOption,
    block: CommandHandlersDsl<C, S, E>.() -> Unit,
  ) {
    commandHandlersBuilder = CommandHandlersBuilder<C, S, E>(defaultWriteOption).apply(block)
  }

  override fun events(
    emptySummary: S,
    defaultCacheOption: CacheOption,
    block: EventHandlersDsl<S, E>.() -> Unit,
  ) {
    eventHandlersBuilder = EventHandlersBuilder<S, E>(emptySummary, defaultCacheOption).apply(block)
  }

  fun build(codecFactory: DataCodecFactory): EpoqueJournal<C, S, E> {
    val (emptySummary, e) = eventHandlersBuilder.build(codecFactory)
    val c = commandHandlersBuilder.build(codecFactory)
    return EpoqueJournal(journalGroupId, emptySummary, e, c)
  }
}
