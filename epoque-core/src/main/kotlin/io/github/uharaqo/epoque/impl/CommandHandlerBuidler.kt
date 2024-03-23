package io.github.uharaqo.epoque.impl

import arrow.core.raise.Raise
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey

fun interface CommandHandlerFactory<C, S, E> {
  fun create(environment: EpoqueEnvironment): CommandHandler<C, S, E>
}

fun interface CommandHandlerBuilderFactory<C, S, E> {
  suspend fun create(): CommandHandlerBuilder<C, S, E>
}

interface CommandHandlerBuilder<C, S, E> : CommandHandlerSideEffects<E>, Raise<Throwable> {

  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Map<Any, Any> = emptyMap())

  fun complete(): CommandHandlerOutput<E>

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw COMMAND_REJECTED.toException(t, message)

  override fun raise(r: Throwable): Nothing = throw r
}

interface CommandHandlerSideEffects<E> {
  suspend fun exists(journal: Journal<*, *>, id: String?): Boolean =
    if (id == null) false else exists(JournalKey(journal.journalGroupId, JournalId(id)))

  suspend fun exists(key: JournalKey): Boolean

  fun notify(block: suspend () -> Unit)

  fun chain(
    id: String,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions.DEFAULT,
    metadata: Map<Any, Any> = emptyMap(),
  ) = chain(JournalId(id), command, options, metadata)

  fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions.DEFAULT,
    metadata: Map<Any, Any> = emptyMap(),
  )
}
