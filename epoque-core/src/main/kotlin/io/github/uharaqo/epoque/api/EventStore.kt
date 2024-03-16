package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import kotlinx.coroutines.flow.Flow

interface TransactionContext

interface EventWriter {
  suspend fun write(
    journalKey: JournalKey,
    events: List<VersionedEvent>,
    tx: TransactionContext,
  ): Either<EventWriteFailure, Unit>
}

interface EventLoader {
  fun queryById(
    journalKey: JournalKey,
    prevVersion: Version,
    tx: TransactionContext,
  ): Either<EventLoadFailure, Flow<VersionedEvent>>
}
