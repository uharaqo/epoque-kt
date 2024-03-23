package io.github.uharaqo.epoque.test.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.test.api.CommandTester
import io.github.uharaqo.epoque.test.api.Tester
import io.github.uharaqo.epoque.test.api.Validator
import kotlinx.coroutines.runBlocking

class DefaultTester(
  val commandRouter: CommandRouter,
  val environment: EpoqueEnvironment,
) : Tester {
  override fun <S, E : Any> forJournal(
    journal: Journal<S, E>,
    block: CommandTester<S, E>.() -> Unit,
  ) {
    DefaultCommandTester(commandRouter, environment, journal).apply(block)
  }
}

class DefaultCommandTester<S, E : Any>(
  val commandRouter: CommandRouter,
  val environment: EpoqueEnvironment,
  val journal: Journal<S, E>,
) : CommandTester<S, E> {
  override fun command(
    id: JournalId,
    command: Any,
    metadata: Map<out Any, Any>,
    block: (Validator<S, E>).() -> Unit,
  ) {
    either {
      val type = CommandType.of(command::class.java)
      val commandCodec = commandRouter.commandCodecRegistry.find<Any>(type).bind()
      val payload = commandCodec.encode(command).bind()
      val input = CommandInput(id, type, payload, metadata)

      val result = runBlocking { commandRouter.process(input) }.bind()

      val summaryProvider =
        SummaryProvider(journal, environment.transactionStarter, environment.eventReader)

      DefaultCommandValidator(input, result, journal, summaryProvider).apply(block)
    }.getOrElse { throw it }
  }
}

class DefaultCommandValidator<S, E : Any>(
  val input: CommandInput,
  result: CommandOutput,
  val journal: Journal<S, E>,
  private val summaryProvider: SummaryProvider<S, E>,
) : Validator<S, E> {

  override val events: List<E> = result.events.map { ve ->
    journal.eventCodecRegistry.find<E>(ve.type)
      .flatMap { codec -> codec.decoder.decode(ve.event) }
      .getOrElse { throw it }
  }

  override val summary: S
    get() = runBlocking { summaryProvider.get(input.id) }
}

class SummaryProvider<S, E : Any>(
  private val journal: Journal<S, E>,
  private val transactionStarter: TransactionStarter,
  override val eventReader: EventReader,
) : CanLoadSummary<S> {
  override val eventHandlerExecutor = journal

  suspend fun get(id: JournalId): S =
    transactionStarter.startDefaultTransaction { tx ->
      loadSummary(JournalKey(journal.journalGroupId, id), tx).map { it.summary }
        .getOrElse { throw it }
    }
      .getOrElse { throw it }
}
