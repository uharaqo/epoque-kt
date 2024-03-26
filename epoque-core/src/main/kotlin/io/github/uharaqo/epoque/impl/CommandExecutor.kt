package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.CallbackHandler
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
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.Cause.TIMEOUT
import io.github.uharaqo.epoque.api.EpoqueException.Cause.UNEXPECTED_ERROR
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.asInputMetadata
import io.github.uharaqo.epoque.api.getRemainingTimeMillis
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironment
import java.time.Instant
import kotlinx.coroutines.TimeoutCancellationException

fun <C : Any, S, E : Any> EpoqueEnvironment.newCommandExecutor(
  journal: Journal<S, E>,
  commandDecoder: CommandDecoder<C>,
  commandHandler: CommandHandler<C, S, E>,
): CommandExecutor<C, S, E> =
  CommandExecutor(
    journalGroupId = journal.journalGroupId,
    commandDecoder = commandDecoder,
    commandHandler = commandHandler,
    eventCodecRegistry = journal.eventCodecRegistry,
    eventHandlerExecutor = journal,
    eventReader = eventReader,
    eventWriter = eventWriter,
    transactionStarter = transactionStarter,
    defaultCommandExecutorOptions = defaultCommandExecutorOptions,
    callbackHandler = callbackHandler ?: CallbackHandler.EMPTY,
  )

/**
 * Execute a command and returns a [CommandOutput]:
 *
 * - Start a transaction as a [TransactionStarter]
 * - Load summary as a [CanLoadSummary]
 * - Run [CommandHandler]
 * - Serialize events as a [CanSerializeEvents]
 * - Write events as a [CanWriteEvents]
 */
class CommandExecutor<C, S, E : Any>(
  val journalGroupId: JournalGroupId,
  val commandDecoder: CommandDecoder<C>,
  val commandHandler: CommandHandler<C, S, E>,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventHandlerExecutor: EventHandlerExecutor<S>,
  override val eventReader: EventReader,
  override val eventWriter: EventWriter,
  val transactionStarter: TransactionStarter,
  val defaultCommandExecutorOptions: CommandExecutorOptions?,
  val callbackHandler: CallbackHandler,
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

    val cbh = callbackHandler + EpoqueRuntimeEnvironment.get()!!

    return EpoqueContext.with(
      {
        put(DeserializedCommand, command)
        put(CommandHandler, commandHandler)
      },
    ) {
      execute(context, command, cbh)
    }
  }

  private suspend fun execute(
    context: CommandContext,
    command: C,
    callbackHandler: CallbackHandler,
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
