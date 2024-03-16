package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.EpoqueException.EventSerializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure

interface EventSerializable<E : Any> {
  val eventCodecRegistry: EventCodecRegistry

  fun serialize(
    prevVersion: Version,
    events: List<E>,
  ): Either<EventSerializationFailure, List<VersionedEvent>> = either {
    events.withIndex().map { (i, e) ->
      val version = prevVersion + (i + 1)

      val eventType = EventType.of(e::class.java)
      val codec = eventCodecRegistry.find<E>(eventType)
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
    prevVersion: Version,
    events: List<E>,
    tx: TransactionContext,
  ): Either<EventWriteFailure, Unit> = either {
    val versionedEvents =
      serialize(prevVersion, events)
        .mapLeft { EventWriteFailure("Failed to serialize event", it) }.bind()

    eventWriter.write(journalKey, versionedEvents, tx).bind()
  }
}
