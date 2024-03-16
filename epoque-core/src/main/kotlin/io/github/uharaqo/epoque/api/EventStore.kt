package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure

interface TransactionContext

interface EventWriter {
  suspend fun write(
    journalKey: JournalKey,
    events: List<VersionedEvent>,
    tx: TransactionContext,
  ): Either<EventWriteFailure, Unit>
}
