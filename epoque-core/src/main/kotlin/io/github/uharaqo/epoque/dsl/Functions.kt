package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.SummaryType
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun Epoque.routerFor(
  vararg journals: Journal<*, *, *>,
  block: EpoqueDsl.() -> Unit,
): DefaultCommandRouter =
  Epoque.routerFor(journals.toList(), block)

fun Epoque.routerFor(
  journals: List<Journal<*, *, *>>,
  block: EpoqueDsl.() -> Unit,
): DefaultCommandRouter =
  EpoqueBuilder(journals).apply(block).build()

inline fun <C : Any, reified S, reified E : Any> Epoque.journalFor(
  codecFactory: DataCodecFactory,
  noinline block: JournalDsl<C, S, E>.() -> Unit,
): Journal<C, S, E> =
  journalFor(JournalGroupId.of<E>(), SummaryType.of<S>(), codecFactory, block)

fun <C : Any, S, E : Any> Epoque.journalFor(
  journalGroupId: JournalGroupId,
  summaryType: SummaryType,
  codecFactory: DataCodecFactory,
  block: JournalDsl<C, S, E>.() -> Unit,
): Journal<C, S, E> =
  JournalBuilder<C, S, E>(journalGroupId, summaryType).apply(block).build(codecFactory)

internal fun <T> T.shouldBeNull(name: String) {
  check(this == null) { "'$name' is already defined" }
}

@OptIn(ExperimentalContracts::class)
internal fun <T> T.shouldBeDefined(name: String): T & Any {
  contract {
    returns() implies (this@shouldBeDefined != null)
  }
  checkNotNull(this) { "'$name' must be defined" }
  return this
}

fun Iterable<Journal<*, *, *>>.toEventCodecRegistry(): EventCodecRegistry =
  EventCodecRegistry(buildMap { this@toEventCodecRegistry.forEach { putAll(it.eventCodecRegistry.registry) } })

fun Iterable<Journal<*, *, *>>.toCommandCodecRegistry(): CommandCodecRegistry =
  CommandCodecRegistry(buildMap { this@toCommandCodecRegistry.forEach { putAll(it.commandCodecRegistry.registry) } })

fun Iterable<Journal<*, Any?, Any>>.toEventHandlerRegistry(): EventHandlerRegistry<Any?, Any> =
  EventHandlerRegistry(
    buildMap { this@toEventHandlerRegistry.forEach { putAll(it.eventHandlerRegistry.registry) } },
  )

fun Iterable<Journal<Any, Any?, Any>>.toCommandProcessorRegistry(
  commandCodecs: CommandCodecRegistry,
  environment: EpoqueEnvironment,
): CommandProcessorRegistry =
  this.asSequence()
    .flatMap { journal ->
      journal.commandHandlers.entries.asSequence().map { (type, setup) ->
        type to newCommandProcessor(setup, journal, commandCodecs, environment)
      }
    }
    .toMap()
    .let { CommandProcessorRegistry(it) }

private fun <C, S, E> newCommandProcessor(
  commandHandlerSetup: CommandHandlerSetup<C, S, E>,
  journal: Journal<C, S, E>,
  commandCodecs: CommandCodecRegistry,
  environment: EpoqueEnvironment,
): CommandProcessor =
  DefaultCommandExecutorFactory(commandHandlerSetup, journal, commandCodecs, environment)
