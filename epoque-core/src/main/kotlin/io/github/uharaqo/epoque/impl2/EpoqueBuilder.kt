package io.github.uharaqo.epoque.impl2

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.builder.JournalBuilder
import io.github.uharaqo.epoque.builder.toRouter

inline fun <reified E : Any> Epoque.forEvent(dataCodecFactory: DataCodecFactory): EpoqueBuilder<E> =
  Epoque.forEvent(JournalGroupId.of<E>(), dataCodecFactory)

fun <E : Any> Epoque.forEvent(
  journalGroupId: JournalGroupId,
  dataCodecFactory: DataCodecFactory,
): EpoqueBuilder<E> =
  EpoqueBuilder(journalGroupId, dataCodecFactory)

fun <C : Any, S, E : Any> Epoque.routerFor(
  journal: Journal<S, E>,
  dataCodecFactory: DataCodecFactory,
  block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
): CommandRouterFactory =
  EpoqueBuilderWithJournal(journal, dataCodecFactory).forCommand(block)

fun Epoque.newRouter(
  environment: EpoqueEnvironment,
  vararg factories: CommandRouterFactory,
): CommandRouter =
  newRouter(environment, factories.toList())

fun Epoque.newRouter(
  environment: EpoqueEnvironment,
  factories: List<CommandRouterFactory>,
): CommandRouter = factories.toRouter(environment)

class EpoqueBuilder<E : Any>(
  val journalGroupId: JournalGroupId,
  val dataCodecFactory: DataCodecFactory,
) {
  fun <S> forSummary(
    emptySummary: S,
    block: JournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    JournalBuilder.create(journalGroupId, emptySummary, dataCodecFactory, block)

  fun <C : Any, S> routerFor(
    journal: Journal<S, E>,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory = this.with(journal).forCommand(block)

  fun <S, E : Any> with(journal: Journal<S, E>): EpoqueBuilderWithJournal<S, E> =
    EpoqueBuilderWithJournal(journal, dataCodecFactory)
}

class EpoqueBuilderWithJournal<S, E : Any>(
  val journal: Journal<S, E>,
  private val dataCodecFactory: DataCodecFactory,
) {
  fun <C : Any> forCommand(
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory =
    CommandRouterFactory.create(journal, dataCodecFactory, block)
}

class DefaultCommandRouter(
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter
