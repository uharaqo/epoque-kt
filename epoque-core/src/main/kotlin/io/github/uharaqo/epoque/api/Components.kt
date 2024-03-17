package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.uharaqo.epoque.api.EpoqueException.EventSerializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import kotlinx.coroutines.flow.toList

interface EventSerializable<E : Any> {
  val eventCodecRegistry: EventCodecRegistry

  fun serialize(
    currentVersion: Version,
    events: List<E>,
  ): Either<EventSerializationFailure, List<VersionedEvent>> = either {
    events.withIndex().map { (i, e) ->
      val version = currentVersion + (i + 1)

      val eventType = EventType.of(e::class.java)
      val codec = eventCodecRegistry.get<E>(eventType)
      val serialized =
        codec
          ?.serialize(e)
          ?.mapLeft { EventSerializationFailure("Failed to serialize event: $eventType", it) }
          ?.bind()
          ?: raise(EventSerializationFailure("EventCodec not found: $eventType"))

      VersionedEvent(version, eventType, serialized)
    }
  }
}

interface EventWritable<E : Any> : EventSerializable<E> {
  val eventWriter: EventWriter

  suspend fun write(
    journalKey: JournalKey,
    currentVersion: Version,
    events: List<E>,
    tx: TransactionContext,
  ): Either<EventWriteFailure, List<VersionedEvent>> = either {
    val versionedEvents =
      serialize(currentVersion, events)
        .mapLeft { EventWriteFailure("Failed to serialize event", it) }.bind()

    eventWriter.write(journalKey, versionedEvents, tx).bind()

    versionedEvents
  }
}

interface SummaryAggregatable<S> {
  val summaryGenerator: SummaryGenerator<S>

  fun aggregate(
    events: List<VersionedEvent>,
    cachedSummary: VersionedSummary<S>?,
  ): Either<SummaryAggregationFailure, VersionedSummary<S>> = either {
    var currentVersion = (cachedSummary?.version ?: Version.ZERO).unwrap
    val initialSummary = cachedSummary?.summary ?: summaryGenerator.emptySummary

    val summary = events.fold(initialSummary) { prevSummary, ve ->
      currentVersion += 1

      ensure(currentVersion == ve.version.unwrap) {
        SummaryAggregationFailure("Event version mismatch. prev: ${currentVersion - 1}, received: ${ve.version}: ${ve.type}")
      }

      summaryGenerator.generateSummary(prevSummary, ve.event).bind()
    }

    VersionedSummary(Version(currentVersion), summary)
  }
}

interface SummaryLoadable<S> : SummaryAggregatable<S> {
  val eventLoader: EventLoader

  suspend fun loadSummary(
    journalKey: JournalKey,
    tx: TransactionContext,
    cachedSummary: VersionedSummary<S>? = null,
  ): Either<EpoqueException, VersionedSummary<S>> = either {
    val prevVersion = cachedSummary?.version ?: Version.ZERO
    val events = eventLoader.queryById(journalKey, prevVersion, tx).bind().toList()

    aggregate(events, cachedSummary).bind()
  }
}
