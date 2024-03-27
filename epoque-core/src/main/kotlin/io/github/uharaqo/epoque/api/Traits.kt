package io.github.uharaqo.epoque.api

import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_HANDLER_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_HANDLER_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.SUMMARY_AGGREGATION_FAILURE
import kotlinx.coroutines.flow.toList

/** Serialize events by using [EventHandlerRegistry]. */
interface CanSerializeEvents<E> {
  val eventCodecRegistry: EventCodecRegistry

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
      val eventType = EventType.of(e!!::class.java)
      val codec = eventCodecRegistry.find<E>(eventType).bind()
      val serialized = codec.encode(e).bind()

      VersionedEvent(version, eventType, serialized)
    }
}

/** Write events by using [EventWriter]. */
interface CanWriteEvents<E> : CanSerializeEvents<E> {
  val eventWriter: EventWriter

  suspend fun writeEvents(
    commandHandlerOutput: CommandHandlerOutput<E>,
    currentVersion: Version,
    context: CommandContext,
    tx: TransactionContext,
  ): Failable<CommandOutput> = either {
    val (events: List<E>, metadata: OutputMetadata) = commandHandlerOutput

    val versionedEvents = serializeEvents(currentVersion, events).bind()
    val output = CommandOutput(versionedEvents, metadata, context)

    eventWriter.writeEvents(output, tx).bind()

    output
  }
}

/**
 * Provide a Summary [S] as an [EventHandlerExecutor] by aggregating events using
 * [EventHandlerRegistry] and [EventCodecRegistry].
 */
interface CanComputeNextSummary<S, E> : EventHandlerExecutor<S> {
  val eventHandlerRegistry: EventHandlerRegistry<S, E>
  val eventCodecRegistry: EventCodecRegistry

  override fun computeNextSummary(
    prevSummary: S,
    eventType: EventType,
    event: SerializedEvent,
  ): Failable<S> = either {
    val eventHandler = eventHandlerRegistry.find(eventType).bind()
    val codec = eventCodecRegistry.find<E>(eventType).bind()
    val deserialized = codec.decode(event).bind()

    catch({ eventHandler.handle(prevSummary, deserialized) }) {
      raise(EVENT_HANDLER_FAILURE.toException(it, eventType.toString()))
    }
  }
}

/** Aggregate events by using [EventHandlerExecutor]. */
interface CanAggregateEvents<S> : EventHandlerExecutor<S> {

  fun aggregateEvents(
    events: List<VersionedEvent>,
    cachedSummary: VersionedSummary<S>?,
  ): Failable<VersionedSummary<S>> = either {
    var currentVersion = (cachedSummary?.version ?: Version.ZERO).unwrap
    val initialSummary = cachedSummary?.summary ?: emptySummary

    val summary = events.fold(initialSummary) { prevSummary, ve ->
      currentVersion += 1

      ensure(currentVersion == ve.version.unwrap) {
        SUMMARY_AGGREGATION_FAILURE.toException(
          message = "Event version mismatch. prev: ${currentVersion - 1}, received: ${ve.version}: ${ve.type}",
        )
      }

      computeNextSummary(prevSummary, ve.type, ve.event).bind()
    }

    VersionedSummary(Version(currentVersion), summary)
  }
}

/**
 * Load events by using [EventReader] and aggregate them as a summary [S]
 * as an [CanAggregateEvents].
 */
interface CanLoadSummary<S> {
  val eventReader: EventReader
  val eventAggregator: CanAggregateEvents<S>

  suspend fun loadSummary(
    key: JournalKey,
    tx: TransactionContext,
    cachedSummary: VersionedSummary<S>? = null,
  ): Failable<VersionedSummary<S>> = either {
    val prevVersion = cachedSummary?.version ?: Version.ZERO
    val events = eventReader.queryById(key, prevVersion, tx).bind().toList()

    eventAggregator.aggregateEvents(events, cachedSummary).bind()
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
interface CanExecuteCommandHandler<C, S, E> :
  CanLoadSummary<S>,
  CanWriteEvents<E> {

  suspend fun execute(
    command: C,
    commandHandler: CommandHandler<C, S, E>,
    context: CommandContext,
    tx: TransactionContext,
  ): Failable<CommandOutput> = either {
    val (currentVersion: Version, currentSummary: S) = loadSummary(context.key, tx).bind()

    val commandHandlerOutput =
      catch({ commandHandler.handle(command, currentSummary) }) { t ->
        raise(if (t is EpoqueException) t else COMMAND_HANDLER_FAILURE.toException(t))
      }

    writeEvents(commandHandlerOutput, currentVersion, context, tx).bind()
  }
}
