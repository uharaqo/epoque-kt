package io.github.uharaqo.epoque

import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.builder.DataCodecFactory
import io.github.uharaqo.epoque.builder.JournalBuilder

object Epoque {
  inline fun <reified E : Any> journalFor(dataCodecFactory: DataCodecFactory): EpoqueBuilder<E> =
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
    block: JournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    EpoqueBuilder<E>(journalGroupId, dataCodecFactory).summaryFor(emptySummary, block)

  fun <C : Any, S, E : Any> routerFor(
    journal: Journal<S, E>,
    dataCodecFactory: DataCodecFactory,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    EpoqueBuilder<E>(journal.journalGroupId, dataCodecFactory).routerFor(journal, block)
}

class EpoqueBuilder<E : Any>(
  val journalGroupId: JournalGroupId,
  val dataCodecFactory: DataCodecFactory,
) {
  fun <S> summaryFor(
    emptySummary: S,
    block: JournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    JournalBuilder.create(journalGroupId, emptySummary, dataCodecFactory, block)

  fun <C : Any, S> routerFor(
    journal: Journal<S, E>,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    CommandRouterFactory.create(journal, dataCodecFactory, block)

  fun <S, E : Any> with(journal: Journal<S, E>): EpoqueBuilderWithJournal<S, E> =
    EpoqueBuilderWithJournal(journal, dataCodecFactory)
}

class EpoqueBuilderWithJournal<S, E : Any>(
  val journal: Journal<S, E>,
  private val dataCodecFactory: DataCodecFactory,
) {
  fun <C : Any> routerFor(
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    CommandRouterFactory.create(journal, dataCodecFactory, block)
}
