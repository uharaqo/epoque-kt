package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.codecFor
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.builder.JournalBuilder
import io.github.uharaqo.epoque.builder.RegistryBuilder
import io.github.uharaqo.epoque.builder.toRouter
import io.github.uharaqo.epoque.dsl.toEventCodec

class EventCodecRegistryBuilder<E : Any>(val codecFactory: DataCodecFactory) {
  private val registry = RegistryBuilder<EventType, EventCodec<*>>()

  /** [CE]: Concrete type of the event */
  inline fun <reified CE : E> register(): EventCodecRegistryBuilder<E> = this.also {
    @Suppress("UNCHECKED_CAST")
    register(codecFactory.codecFor<CE>() as DataCodec<E>)
  }

  fun register(codec: DataCodec<E>): EventCodecRegistryBuilder<E> =
    this.also { registry[EventType.of(codec.type)] = codec.toEventCodec() }

  fun build(): EventCodecRegistry = EventCodecRegistry(
    registry.build { EpoqueException.Cause.EVENT_NOT_SUPPORTED.toException(message = it.toString()) },
  )
}

inline fun <reified E : Any> Epoque.journalFor(dataCodecFactory: DataCodecFactory): EpoqueBuilder<E> =
  journalFor(JournalGroupId.of<E>(), dataCodecFactory)

fun <E : Any> Epoque.journalFor(
  journalGroupId: JournalGroupId,
  dataCodecFactory: DataCodecFactory,
): EpoqueBuilder<E> =
  EpoqueBuilder(journalGroupId, dataCodecFactory)

fun <C : Any, S, E : Any> Epoque.routerFor(
  journal: Journal<S, E>,
  dataCodecFactory: DataCodecFactory,
  block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
): CommandRouterFactory =
  EpoqueBuilderWithJournal(journal, dataCodecFactory).routerFor(block)

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
  fun <S> summaryFor(
    emptySummary: S,
    block: JournalBuilder<S, E>.() -> Unit,
  ): Journal<S, E> =
    JournalBuilder.create(journalGroupId, emptySummary, dataCodecFactory, block)

  fun <C : Any, S> routerFor(
    journal: Journal<S, E>,
    block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
  ): CommandRouterFactory = this.with(journal).routerFor(block)

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
