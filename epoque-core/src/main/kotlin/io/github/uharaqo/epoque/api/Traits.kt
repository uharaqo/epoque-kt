package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.uharaqo.epoque.api.EpoqueException.CommandDeserializationException
import io.github.uharaqo.epoque.api.EpoqueException.EventSerializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.api.EpoqueException.TimeoutException
import io.github.uharaqo.epoque.api.EpoqueException.UnknownException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.toList

interface CanSerializeEvents<E : Any> {
  val eventCodecRegistry: EventCodecRegistry<E>

  fun serializeEvents(
    currentVersion: Version,
    events: List<E>,
  ): Either<EventSerializationFailure, List<VersionedEvent>> = either {
    events.withIndex().map { (i, e) ->
      val version = currentVersion + (i + 1)
      serializeEvent(e, version)
    }.bindAll()
  }

  fun serializeEvent(e: E, version: Version): Either<EventSerializationFailure, VersionedEvent> =
    either {
      val eventType = EventType.of(e::class.java)
      val serialized =
        eventCodecRegistry.find(eventType)
          .flatMap { it.serialize(e) }
          .mapLeft { EventSerializationFailure("Failed to serialize event: $eventType", it) }
          .bind()

      VersionedEvent(version, eventType, serialized)
    }
}

interface CanWriteEvents<E : Any> : CanSerializeEvents<E> {
  val eventWriter: EventWriter

  suspend fun writeEvents(
    journalKey: JournalKey,
    currentVersion: Version,
    events: List<E>,
    tx: TransactionContext,
  ): Either<EventWriteFailure, List<VersionedEvent>> = either {
    val versionedEvents =
      serializeEvents(currentVersion, events)
        .mapLeft { EventWriteFailure("Failed to serialize event", it) }.bind()

    eventWriter.write(journalKey, versionedEvents, tx).bind()

    versionedEvents
  }
}

interface CanAggregateEvents<S> {
  val eventHandlerExecutor: EventHandlerExecutor<S>

  fun aggregateEvents(
    events: List<VersionedEvent>,
    cachedSummary: VersionedSummary<S>?,
  ): Either<SummaryAggregationFailure, VersionedSummary<S>> = either {
    var currentVersion = (cachedSummary?.version ?: Version.ZERO).unwrap
    val initialSummary = cachedSummary?.summary ?: eventHandlerExecutor.emptySummary

    val summary = events.fold(initialSummary) { prevSummary, ve ->
      currentVersion += 1

      ensure(currentVersion == ve.version.unwrap) {
        SummaryAggregationFailure("Event version mismatch. prev: ${currentVersion - 1}, received: ${ve.version}: ${ve.type}")
      }

      eventHandlerExecutor.computeNextSummary(prevSummary, ve.type, ve.event).bind()
    }

    VersionedSummary(Version(currentVersion), summary)
  }
}

interface CanLoadSummary<S> : CanAggregateEvents<S> {
  val eventLoader: EventLoader

  suspend fun loadSummary(
    journalKey: JournalKey,
    tx: TransactionContext,
    cachedSummary: VersionedSummary<S>? = null,
  ): Either<EpoqueException, VersionedSummary<S>> = either {
    val prevVersion = cachedSummary?.version ?: Version.ZERO
    val events = eventLoader.queryById(journalKey, prevVersion, tx).bind().toList()

    aggregateEvents(events, cachedSummary).bind()
  }
}

interface CanExecuteCommandHandler<C, S, E : Any> :
  CanLoadSummary<S>,
  CanWriteEvents<E>,
  TransactionStarter {

  val journalGroupId: JournalGroupId
  val commandHandler: CommandHandler<C, S, E>

  suspend fun execute(journalId: JournalId, command: C): Either<EpoqueException, CommandOutput> =
    either {
      val journalKey = JournalKey(journalGroupId, journalId)
      val timeoutMillis = 10L // TODO

      return startTransactionAndLock(journalKey) { tx ->
        withTimeout(timeoutMillis) {
          execute(journalKey, command, tx).bind()
        }.bind() // make sure to throw an exception for rollback
      }
    }

  suspend fun execute(
    journalKey: JournalKey,
    command: C,
    tx: TransactionContext,
  ): Either<EpoqueException, CommandOutput> = either {
    val (currentVersion: Version, currentSummary: S) = loadSummary(journalKey, tx).bind()

    val events = commandHandler.handle(command, currentSummary).bind()

    val versionedEvents = writeEvents(journalKey, currentVersion, events, tx).bind()

    CommandOutput(versionedEvents)
  }

  private suspend inline fun <T> withTimeout(
    timeoutMillis: Long,
    crossinline block: suspend () -> T,
  ): Either<EpoqueException, T> =
    Either.catch { kotlinx.coroutines.withTimeout(timeoutMillis) { block() } }
      .mapLeft {
        when (it) {
          is EpoqueException -> it
          is TimeoutCancellationException -> TimeoutException("Timeout", it)
          else -> UnknownException("Unexpected failure", it)
        }
      }
}

interface CanProcessCommand<C> : CommandProcessor {
  val commandCodec: CommandCodec<C>
  val canExecuteCommandHandler: CanExecuteCommandHandler<C, *, *>

  override suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput> =
    either {
      val command =
        commandCodec.deserialize(input.payload)
          .mapLeft { raise(CommandDeserializationException("Failed to deserialize command: ${input.type}")) }
          .bind()

      canExecuteCommandHandler.execute(input.id, command).bind()
    }
}
