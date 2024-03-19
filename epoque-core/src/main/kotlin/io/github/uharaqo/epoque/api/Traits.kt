package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.uharaqo.epoque.api.EpoqueException.Cause.SUMMARY_AGGREGATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.TIMEOUT_EXCEPTION
import io.github.uharaqo.epoque.api.EpoqueException.Cause.UNKNOWN_EXCEPTION
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/** Serialize events by using [EventHandlerRegistry]. */
interface CanSerializeEvents<E : Any> {
  val eventCodecRegistry: EventCodecRegistry<E>

  fun serializeEvents(
    currentVersion: Version,
    events: List<E>,
  ): Failable<List<VersionedEvent>> = either {
    events.withIndex().map { (i, e) ->
      val version = currentVersion + (i + 1)
      serializeEvent(e, version)
    }.bindAll()
  }

  fun serializeEvent(e: E, version: Version): Failable<VersionedEvent> =
    either {
      val eventType = EventType.of(e::class.java)
      val codec = eventCodecRegistry.find(eventType).bind()
      val serialized = codec.serialize(e).bind()

      VersionedEvent(version, eventType, serialized)
    }
}

/** Write events by using [EventWriter]. */
interface CanWriteEvents<E : Any> {
  val eventWriter: EventWriter
}

/**
 * Provide a Summary [S] as an [EventHandlerExecutor] by aggregating events using
 * [EventHandlerRegistry] and [EventCodecRegistry].
 */
interface CanComputeNextSummary<S, E : Any> : EventHandlerExecutor<S> {
  val eventHandlerRegistry: EventHandlerRegistry<S, E>
  val eventCodecRegistry: EventCodecRegistry<E>

  override fun computeNextSummary(
    prevSummary: S,
    eventType: EventType,
    event: SerializedEvent,
  ): Failable<S> = either {
    val eventHandler = eventHandlerRegistry.find(eventType).bind()

    val codec = eventCodecRegistry.find(eventType).bind()
    val deserialized = codec.deserialize(event).bind()

    eventHandler.handle(prevSummary, deserialized).bind()
  }
}

/** Aggregate events by using [EventHandlerExecutor]. */
interface CanAggregateEvents<S> {
  val eventHandlerExecutor: EventHandlerExecutor<S>

  fun aggregateEvents(
    events: List<VersionedEvent>,
    cachedSummary: VersionedSummary<S>?,
  ): Failable<VersionedSummary<S>> = either {
    var currentVersion = (cachedSummary?.version ?: Version.ZERO).unwrap
    val initialSummary = cachedSummary?.summary ?: eventHandlerExecutor.emptySummary

    val summary = events.fold(initialSummary) { prevSummary, ve ->
      currentVersion += 1

      ensure(currentVersion == ve.version.unwrap) {
        SUMMARY_AGGREGATION_FAILURE.toException(
          "Event version mismatch. prev: ${currentVersion - 1}, received: ${ve.version}: ${ve.type}",
        )
      }

      eventHandlerExecutor.computeNextSummary(prevSummary, ve.type, ve.event).bind()
    }

    VersionedSummary(Version(currentVersion), summary)
  }
}

/**
 * Load events by using [EventLoader] and aggregate them as a summary [S]
 * as an [CanAggregateEvents].
 */
interface CanLoadSummary<S> : CanAggregateEvents<S> {
  val eventLoader: EventLoader

  suspend fun loadSummary(
    key: JournalKey,
    tx: TransactionContext,
    cachedSummary: VersionedSummary<S>? = null,
  ): Failable<VersionedSummary<S>> = either {
    val prevVersion = cachedSummary?.version ?: Version.ZERO
    val events = eventLoader.queryById(key, prevVersion, tx).bind().toList()

    aggregateEvents(events, cachedSummary).bind()
  }
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
interface CanExecuteCommandHandler<C, S, E : Any> :
  CanLoadSummary<S>,
  CanSerializeEvents<E>,
  CanWriteEvents<E>,
  TransactionStarter {

  val journalGroupId: JournalGroupId
  val commandHandler: CommandHandler<C, S, E>
  val defaultCommandExecutorOptions: CommandExecutorOptions?
  val callbackHandler: CallbackHandler?

  suspend fun execute(command: C, context: CommandContext): Failable<CommandOutput> =
    either {
      catchWithTimeout(context.options.timeoutMillis) {
        callbackHandler?.beforeBegin(context)

        startTransactionAndLock(context.key, context.options.lockOption) { tx ->
          callbackHandler?.afterBegin(context)

          execute(command, commandHandler, context, tx).bind()
            .also { callbackHandler?.beforeCommit(context, it) }
        }.bind()
      }.bind()
    }
      .onRight { callbackHandler?.afterCommit(context, it) }
      .onLeft { callbackHandler?.afterRollback(context, it) }

  suspend fun execute(
    command: C,
    commandHandler: CommandHandler<C, S, E>,
    context: CommandContext,
    tx: TransactionContext,
  ): Failable<CommandOutput> = either {
    val (currentVersion: Version, currentSummary: S) = loadSummary(context.key, tx).bind()

    val events = commandHandler.handle(command, currentSummary).bind()

    val versionedEvents = serializeEvents(currentVersion, events).bind()

    val output = CommandOutput(versionedEvents, context)

    eventWriter.writeEvents(output, tx).bind()

    output
  }

  private suspend inline fun <T> catchWithTimeout(
    timeoutMillis: Long,
    crossinline block: suspend () -> T,
  ): Failable<T> =
    Either.catch { kotlinx.coroutines.withTimeout(timeoutMillis) { block() } }
      .mapLeft {
        when (it) {
          is EpoqueException -> it
          is TimeoutCancellationException -> TIMEOUT_EXCEPTION.toException(it)
          else -> UNKNOWN_EXCEPTION.toException(it)
        }
      }
}

/** Deserialize a command by using [CommandCodec] and execute it by using [CanExecuteCommandHandler]. */
interface CanProcessCommand<C> : CommandProcessor {
  val commandCodec: CommandCodec<C>
  val executor: CanExecuteCommandHandler<C, *, *>

  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    either {
      val command = commandCodec.deserialize(input.payload).bind()

      val commandContext = CommandContext(
        key = JournalKey(executor.journalGroupId, input.id),
        commandType = input.type,
        command = input.payload,
        options =
        input.commandExecutorOptions
          ?: executor.defaultCommandExecutorOptions
          ?: CommandExecutorOptions(),
      )

      val epoqueContext =
        EpoqueContext.create()
          .with(CommandContext.Key, commandContext)
          .with(EventCodecRegistry.Key, executor.eventCodecRegistry)
          .with(CommandCodec.Key, commandCodec)

      withContext(coroutineContext + epoqueContext) {
        executor.execute(command, commandContext).bind()
      }
    }
}
