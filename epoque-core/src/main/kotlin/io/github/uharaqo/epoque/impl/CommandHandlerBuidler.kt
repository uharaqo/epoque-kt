package io.github.uharaqo.epoque.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.Raise
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutputMetadata
import java.util.concurrent.ConcurrentLinkedQueue

interface CommandHandlerBuilder<C, S, E> : Raise<Throwable> {

  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Map<Any, Any> = emptyMap())

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw COMMAND_REJECTED.toException(t, message)

  suspend fun exists(journal: Journal<*, *>, id: String?): Boolean =
    if (id == null) false else exists(JournalKey(journal.journalGroupId, JournalId(id)))

  suspend fun exists(key: JournalKey): Boolean

  override fun raise(r: Throwable): Nothing = throw r

  fun chain(
    id: String,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions(),
    metadata: Map<Any, Any> = emptyMap(),
  ) = chain(JournalId(id), command, options, metadata)

  fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions(),
    metadata: Map<Any, Any> = emptyMap(),
  )
}

fun interface CommandHandlerRuntimeEnvironmentFactory<C : Any, S, E : Any> {
  suspend fun create(): CommandHandlerRuntimeEnvironment<C, S, E>
}

class DefaultCommandHandler<C : Any, S, E : Any>(
  private val impl: suspend CommandHandlerBuilder<C, S, E>.(C, S) -> Unit,
  private val runtimeEnvFactory: CommandHandlerRuntimeEnvironmentFactory<C, S, E>,
) : CommandHandler<C, S, E> {
  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> =
    runtimeEnvFactory.create().apply { impl(c, s) }.complete()
}

class CommandHandlerRuntimeEnvironment<C, S, E>(
  private val journalChecker: JournalChecker,
  private val commandCodecRegistry: CommandCodecRegistry?,
) : CommandHandlerBuilder<C, S, E> {
  private val events = ConcurrentLinkedQueue<E>()
  private val chainedCommands = ConcurrentLinkedQueue<CommandInput>()
  private var metadata = mutableMapOf<Any, Any>()

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  override suspend fun exists(key: JournalKey): Boolean =
    journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      commandCodecRegistry!!.find<Any>(type).flatMap { it.encode(command) }.getOrElse { throw it }
    chainedCommands += CommandInput(id, type, serialized, metadata, options)
  }

  fun complete(): CommandHandlerOutput<E> {
    return CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())
  }
}
