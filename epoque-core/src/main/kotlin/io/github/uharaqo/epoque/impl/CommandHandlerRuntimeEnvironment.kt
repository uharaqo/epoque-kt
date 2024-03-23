package io.github.uharaqo.epoque.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutputMetadata
import java.util.concurrent.ConcurrentLinkedQueue

/** Created for each command request to store temporary state for the process */
class CommandHandlerRuntimeEnvironment<E>(
  private val journalChecker: JournalChecker,
  private val router: CommandRouter,
) : CommandHandlerSideEffects<E>, CallbackHandler {
  private val chainedCommands = ConcurrentLinkedQueue<CommandInput>()
  private val notify = ConcurrentLinkedQueue<suspend () -> Unit>()

  override suspend fun exists(key: JournalKey): Boolean =
    journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }

  override fun notify(block: suspend () -> Unit) {
    notify += block
  }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      router.commandCodecRegistry.find<Any>(type).flatMap { it.encode(command) }
        .getOrElse { throw it }
    chainedCommands += CommandInput(id, type, serialized, metadata, options)
  }

  override suspend fun beforeBegin(context: CommandContext) {
  }

  override suspend fun afterCommit(output: CommandOutput) {
    notify.forEach { it() }
  }

  companion object : EpoqueContextKey<CommandHandlerRuntimeEnvironment<*>>
}

class DefaultCommandHandlerRuntime<C, S, E>(
  private val runtimeEnvironment: CommandHandlerRuntimeEnvironment<E>,
) : CommandHandlerBuilder<C, S, E>, CommandHandlerSideEffects<E> by runtimeEnvironment {
  private val events = ConcurrentLinkedQueue<E>()
  private var metadata = mutableMapOf<Any, Any>()

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  override fun complete(): CommandHandlerOutput<E> {
    return CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())
  }
}
