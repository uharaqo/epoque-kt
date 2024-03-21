package io.github.uharaqo.epoque

import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.impl.CommandRouterFactory
import io.github.uharaqo.epoque.impl.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.impl.DefaultJournalBuilder

object Epoque {
  inline fun <reified E : Any> journalFor(
    dataCodecFactory: DataCodecFactory,
  ): EpoqueBuilder<E> =
    journalFor(JournalGroupId.of<E>(), dataCodecFactory)

  fun <E : Any> journalFor(
    journalGroupId: JournalGroupId,
    dataCodecFactory: DataCodecFactory,
  ): EpoqueBuilder<E> =
    EpoqueBuilder(journalGroupId, dataCodecFactory)

  fun <S, E : Any> journalFor(
    journalGroupId: JournalGroupId,
    emptySummary: S,
    dataCodecFactory: DataCodecFactory,
    block: DefaultJournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    EpoqueBuilder<E>(journalGroupId, dataCodecFactory).summaryFor(emptySummary, block)

  fun <C : Any, S, E : Any> commandRouterFactoryFor(
    journal: Journal<S, E>,
    dataCodecFactory: DataCodecFactory,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    EpoqueBuilder<E>(journal.journalGroupId, dataCodecFactory)
      .commandRouterFactoryFor(journal, block as CommandRouterFactoryBuilder<C, *, *>.() -> Unit)
}

data class EpoqueBuilder<E : Any>(
  val journalGroupId: JournalGroupId,
  val dataCodecFactory: DataCodecFactory,
) {
  fun <S> summaryFor(
    emptySummary: S,
    block: DefaultJournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    DefaultJournalBuilder<S, E>(journalGroupId, emptySummary, dataCodecFactory).apply(block).build()

  fun <C : Any, S, E : Any> commandRouterFactoryFor(
    journal: Journal<S, E>,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    CommandRouterFactoryBuilder<C, _, _>(journal, dataCodecFactory).also(block).build()
}
