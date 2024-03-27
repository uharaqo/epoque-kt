package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.SummaryType

class JournalBuilder<C : Any, S, E : Any>(
  val journalGroupId: JournalGroupId,
  val summaryType: SummaryType,
) : JournalDsl<C, S, E>() {
  private var commandHandlersBuilder: CommandHandlersBuilder<C, S, E>? = null
  private var eventHandlersBuilder: EventHandlersBuilder<S, E>? = null
  private var emptySummary: Lazy<S>? = null // ternary: not-set, null, non-null

  override fun commands(
    block: CommandHandlersDsl<C, S, E>.() -> Unit,
  ) {
    commandHandlersBuilder.shouldBeNull("commands")
    commandHandlersBuilder = CommandHandlersBuilder<C, S, E>().apply(block)
  }

  override fun events(
    emptySummary: S,
    block: EventHandlersDsl<S, E>.() -> Unit,
  ) {
    eventHandlersBuilder.shouldBeNull("events")
    this.emptySummary = lazy { emptySummary }
    eventHandlersBuilder = EventHandlersBuilder<S, E>().apply(block)
  }

  fun build(codecFactory: DataCodecFactory): Journal<C, S, E> {
    val eventHandlers = eventHandlersBuilder.shouldBeDefined("events").build(codecFactory)
    val commandHandlers = commandHandlersBuilder.shouldBeDefined("commands").build(codecFactory)

    val eventHandlerRegistry =
      eventHandlers.entries
        .associate { (type, setup) -> type to setup.handler }
        .let(::EventHandlerRegistry)

    val eventCodecRegistry =
      eventHandlers.entries
        .associate { (type, setup) -> type to setup.codec }
        .let(::EventCodecRegistry)

    val commandCodecRegistry: CommandCodecRegistry =
      commandHandlers.entries
        .associate { (type, setup) -> type to setup.codec }
        .let(::CommandCodecRegistry)

    return Journal(
      journalGroupId,
      summaryType,
      emptySummary!!.value,
      summaryCache,
      eventCodecRegistry,
      eventHandlerRegistry,
      commandHandlers,
      commandCodecRegistry,
    )
  }
}
