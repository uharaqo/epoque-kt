package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CanExecuteCommandHandler
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.CanSerializeEvents
import io.github.uharaqo.epoque.api.CanWriteEvents
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandDecoder
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_DECODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_ENCODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_DECODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_ENCODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.TIMEOUT
import io.github.uharaqo.epoque.api.EpoqueException.Cause.UNEXPECTED_ERROR
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.Registry
import io.github.uharaqo.epoque.api.SerializedCommand
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.asInputMetadata
import io.github.uharaqo.epoque.api.getRemainingTimeMillis
import java.time.Instant
import java.util.*
import kotlinx.coroutines.TimeoutCancellationException

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
  val commandDecoder: CommandDecoder<C>,
  val commandHandler: CommandHandler<C, S, E>,
  val callbackHandler: CallbackHandler,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventHandlerExecutor: EventHandlerExecutor<S>,
  override val eventReader: EventReader,
  override val eventWriter: EventWriter,
  val transactionStarter: TransactionStarter,
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

    return EpoqueContext.with(
      { put(DeserializedCommand, command).put(CommandHandler, commandHandler) },
    ) {
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

object DeserializedCommand : EpoqueContextKey<Any>

class DefaultRegistry<K : Any, V : Any>(
  map: Map<K, V>,
  private val onError: (K) -> EpoqueException,
) : Registry<K, V> {
  private val map = Collections.unmodifiableMap(map)

  override fun find(key: K): Failable<V> = either { map[key] ?: raise(onError(key)) }

  override fun toMap(): Map<K, V> = map
}

fun <E : Any> DataCodec<E>.toEventCodec(): EventCodec<E> =
  DataCodecAdapter(
    this,
    ::SerializedEvent,
    SerializedEvent::unwrap,
    EVENT_NOT_SUPPORTED,
    EVENT_ENCODING_FAILURE,
    EVENT_DECODING_FAILURE,
  )
    .let { EventCodec(it::encode, it::decode) }

fun <C : Any> DataCodec<C>.toCommandCodec(): CommandCodec<C> =
  DataCodecAdapter(
    this,
    ::SerializedCommand,
    SerializedCommand::unwrap,
    COMMAND_NOT_SUPPORTED,
    COMMAND_ENCODING_FAILURE,
    COMMAND_DECODING_FAILURE,
  )
    .let { CommandCodec(it::encode, it::decode) }

private class DataCodecAdapter<V : Any, W>(
  private val codec: DataCodec<V>,
  private val wrapper: (SerializedData) -> W,
  private val unwrapper: (W) -> SerializedData,
  private val notSupported: EpoqueException.Cause,
  private val encodingFailure: EpoqueException.Cause,
  private val decodingFailure: EpoqueException.Cause,
) {
  fun encode(v: V): Failable<W> = either {
    if (!codec.type.isInstance(v)) {
      raise(notSupported.toException(message = v::class.java.canonicalName ?: v.toString()))
    }

    catch(
      { codec.encode(v).let(wrapper) },
    ) { raise(encodingFailure.toException(it, codec.type.toString())) }
  }

  fun decode(serialized: W): Failable<V> = either {
    catch(
      { codec.decode(unwrapper(serialized)) },
    ) { raise(decodingFailure.toException(it, codec.type.toString())) }
      .also {
        if (!codec.type.isInstance(it)) {
          raise(notSupported.toException(message = it::class.java.canonicalName ?: it.toString()))
        }
      }
  }
}
