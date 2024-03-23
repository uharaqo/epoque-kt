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
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException
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
import kotlinx.coroutines.TimeoutCancellationException
import java.time.Instant

fun interface CommandHandlerFactory<C, S, E : Any> {
  fun create(environment: EpoqueEnvironment): CommandHandler<C, S, E>
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
class CommandExecutor<C, S, E : Any>(
  val journalGroupId: JournalGroupId,
  private val commandDecoder: CommandDecoder<C>,
  private val commandHandler: CommandHandler<C, S, E>,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventHandlerExecutor: EventHandlerExecutor<S>,
  override val eventReader: EventReader,
  override val eventWriter: EventWriter,
  private val transactionStarter: TransactionStarter,
  private val defaultCommandExecutorOptions: CommandExecutorOptions?,
  private val callbackHandler: CallbackHandler,
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

    val taskManager = CommandHandlerRuntimeEnvironment(eventReader, CommandRouter.get()!!)
    val cbh = callbackHandler + taskManager

    return EpoqueContext.with({ put(CommandHandlerRuntimeEnvironment, taskManager) }) {
      execute(context, command, cbh)
    }
  }

  private suspend fun execute(
    context: CommandContext,
    command: C,
    cbh: CallbackHandler,
  ): Either<EpoqueException, CommandOutput> =
    either {
      catchWithTimeout(context.options.timeoutMillis) {
        cbh.beforeBegin(context)

        transactionStarter.startTransactionAndLock(context.key, context.options.lockOption) { tx ->
          cbh.afterBegin(context)

          execute(command, commandHandler, context, tx).bind()
            .also { cbh.beforeCommit(it) }
        }.bind()
      }.bind()
    }
      .onRight { cbh.afterCommit(it) }
      .onLeft { cbh.afterRollback(context, it) }

  private suspend inline fun <T> catchWithTimeout(
    timeoutMillis: Long,
    crossinline block: suspend () -> T,
  ): Failable<T> =
    Either.catch { kotlinx.coroutines.withTimeout(timeoutMillis) { block() } }
      .mapLeft {
        when (it) {
          is EpoqueException -> it
          is TimeoutCancellationException -> EpoqueException.Cause.TIMEOUT_EXCEPTION.toException(it)
          else -> EpoqueException.Cause.UNKNOWN_EXCEPTION.toException(it)
        }
      }

  private fun CommandInput.getCommandExecutorOptions(): CommandExecutorOptions =
    commandExecutorOptions
      ?: defaultCommandExecutorOptions
      ?: CommandExecutorOptions()

  private suspend fun CommandExecutorOptions.refreshTimeoutMillis(): CommandExecutorOptions =
    CommandContext.get()?.getRemainingTimeMillis()
      ?.let { this.copy(timeoutMillis = it) }
      ?: this

  companion object {
    fun <C : Any, S, E : Any> create(
      journal: Journal<S, E>,
      commandDecoder: CommandDecoder<C>,
      commandHandlerFactory: CommandHandlerFactory<C, S, E>,
      environment: EpoqueEnvironment,
    ): CommandExecutor<C, S, E> =
      CommandExecutor(
        journalGroupId = journal.journalGroupId,
        commandDecoder = commandDecoder,
        commandHandler = commandHandlerFactory.create(environment),
        eventCodecRegistry = journal.eventCodecRegistry,
        eventHandlerExecutor = journal.toEventHandlerExecutor(),
        eventReader = environment.eventReader,
        eventWriter = environment.eventWriter,
        transactionStarter = environment.transactionStarter,
        defaultCommandExecutorOptions = environment.defaultCommandExecutorOptions,
        callbackHandler = environment.callbackHandler ?: CallbackHandler.EMPTY,
      )
  }
}

object DeserializedCommand : EpoqueContextKey<Any>
