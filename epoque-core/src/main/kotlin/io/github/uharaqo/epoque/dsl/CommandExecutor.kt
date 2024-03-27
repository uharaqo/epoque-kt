package io.github.uharaqo.epoque.dsl

import arrow.core.Either
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CanAggregateEvents
import io.github.uharaqo.epoque.api.CanExecuteCommandHandler
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.CanSerializeEvents
import io.github.uharaqo.epoque.api.CanWriteEvents
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandDecoder
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.DeserializedCommand
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.Cause.TIMEOUT
import io.github.uharaqo.epoque.api.EpoqueException.Cause.UNEXPECTED_ERROR
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SummaryCache
import io.github.uharaqo.epoque.api.SummaryId
import io.github.uharaqo.epoque.api.SummaryType
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.VersionedSummary
import io.github.uharaqo.epoque.api.asInputMetadata
import io.github.uharaqo.epoque.api.getRemainingTimeMillis
import java.time.Instant
import kotlinx.coroutines.TimeoutCancellationException

/** Create a [CommandExecutor] and executes it */
interface CommandExecutorFactory<C, S, E> : CommandProcessor {
  suspend fun create(): CommandExecutor<C, S, E>

  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    create().process(input)
}

/**
 * Execute a command and returns a [CommandOutput]:
 *
 * - Start a transaction as a [TransactionStarter]
 * - Load summary as a [CanLoadSummary]
 * - Run [CommandHandler]
 * - Serialize events as a [CanSerializeEvents]
 * - Write events as a [CanWriteEvents]
 */
class CommandExecutor<C, S, E>(
  val journalGroupId: JournalGroupId,
  val summaryType: SummaryType,
  val commandDecoder: CommandDecoder<C>,
  val commandHandler: CommandHandler<C, S, E>,
  val callbackHandler: CallbackHandler,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventAggregator: CanAggregateEvents<S>,
  override val eventReader: EventReader,
  override val eventWriter: EventWriter,
  val transactionStarter: TransactionStarter,
  val cache: SummaryCache?,
  val defaultCommandExecutorOptions: CommandExecutorOptions?,
) : CommandProcessor, CanExecuteCommandHandler<C, S, E> {

  override suspend fun process(input: CommandInput): Failable<CommandOutput> = either {
    val command = commandDecoder.decode(input.payload).bind()

    val context =
      CommandContext(
        key = JournalKey(journalGroupId, input.id),
        commandType = input.type,
        command = input.payload,
        metadata = input.metadata.asInputMetadata(),
        options = input.getCommandExecutorOptions().refreshTimeoutMillis(),
        receivedTime = Instant.now(),
      )

    return EpoqueContext.with({ put(DeserializedCommand, command) }) {
      execute(context, command)
    }
  }

  private suspend fun execute(
    context: CommandContext,
    command: C,
  ): Either<EpoqueException, CommandOutput> =
    either {
      catchWithTimeout(context.options.timeoutMillis) {
        callbackHandler.beforeBegin(context)

        transactionStarter.startTransactionAndLock(context.key, context.options.writeOption) { tx ->
          callbackHandler.afterBegin(context)

          execute(command, commandHandler, context, tx).bind()
            .also { callbackHandler.beforeCommit(it) }
        }.bind()
      }.bind()
    }
      .onRight { callbackHandler.afterCommit(it) }
      .onLeft { callbackHandler.afterRollback(context, it) }

  override suspend fun loadSummary(
    key: JournalKey,
    tx: TransactionContext,
    cachedSummary: VersionedSummary<S>?,
  ): Failable<VersionedSummary<S>> {
    val summaryId = SummaryId(summaryType, key)
    val cached = cachedSummary ?: cache?.get(summaryId)
    return super.loadSummary(key, tx, cached)
      .onRight { cache?.set(summaryId, it) }
  }

  private suspend inline fun <T> catchWithTimeout(
    timeoutMillis: Long,
    crossinline block: suspend () -> T,
  ): Failable<T> =
    Either.catch { kotlinx.coroutines.withTimeout(timeoutMillis) { block() } }
      .mapLeft {
        when (it) {
          is EpoqueException -> it
          is TimeoutCancellationException -> TIMEOUT.toException(it)
          else -> UNEXPECTED_ERROR.toException(it)
        }
      }

  private fun CommandInput.getCommandExecutorOptions(): CommandExecutorOptions =
    commandExecutorOptions
      ?: defaultCommandExecutorOptions
      ?: CommandExecutorOptions.DEFAULT

  private suspend fun CommandExecutorOptions.refreshTimeoutMillis(): CommandExecutorOptions =
    CommandContext.get()?.getRemainingTimeMillis()
      ?.let { this.copy(timeoutMillis = it) }
      ?: this
}
