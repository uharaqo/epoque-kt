package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import arrow.core.raise.Raise
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.Metadata
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutput
import java.util.concurrent.ConcurrentLinkedQueue

interface CommandHandlerBuilder<C, S, E> : Raise<Throwable> {

  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Metadata = Metadata.empty)

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw EpoqueException.Cause.COMMAND_REJECTED.toException(t, message)

  suspend fun exists(journal: Journal<*, *>, id: String): Boolean =
    exists(JournalKey(journal.journalGroupId, JournalId(id)))

  suspend fun exists(key: JournalKey): Boolean

  override fun raise(r: Throwable): Nothing = throw r
}

class DefaultCommandHandlerBuilder<C, S, E>(
  private val journalChecker: JournalChecker,
) : CommandHandlerBuilder<C, S, E> {
  private val events = ConcurrentLinkedQueue<E>()
  private var metadata = Metadata.empty

  override fun emit(events: List<E>, metadata: Metadata) {
    this.events += events
    this.metadata += metadata
  }

  override suspend fun exists(key: JournalKey): Boolean =
    journalChecker.journalExists(key, TransactionContext.Key.get()!!).getOrElse { throw it }

  fun complete(): CommandHandlerOutput<E> =
    CommandHandlerOutput(events.toList(), metadata.asOutput())
}
