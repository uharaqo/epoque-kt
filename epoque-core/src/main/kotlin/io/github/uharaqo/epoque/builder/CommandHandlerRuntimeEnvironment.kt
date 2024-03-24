package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.impl.DefaultEpoqueRuntimeEnvironmentFactoryFactory

/** build time */
interface EpoqueRuntimeEnvironmentFactoryFactory {
  fun create(
    commandRouter: CommandRouter,
    environment: EpoqueEnvironment,
  ): EpoqueRuntimeEnvironmentFactory<Any, Any?, Any>

  companion object {
    fun create(): EpoqueRuntimeEnvironmentFactoryFactory =
      DefaultEpoqueRuntimeEnvironmentFactoryFactory()
  }
}

/** routing time */
interface EpoqueRuntimeEnvironmentFactory<C, S, E> : CommandProcessor, CommandRouter {
  suspend fun create(): EpoqueRuntimeEnvironment<C, S, E>
}

/** execution time */
interface EpoqueRuntimeEnvironment<C, S, E> :
  CommandHandlerOutputCollector<E>, CommandHandlerSideEffectHandler, CallbackHandler {

  companion object : EpoqueContextKey<EpoqueRuntimeEnvironment<*, *, *>>
}

interface WithPreparedParam {

  fun <X> getPreparedParam(): X?
}

interface CommandHandlerOutputCollector<E> {
  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Map<Any, Any> = emptyMap())

  fun complete(): CommandHandlerOutput<E>

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw COMMAND_REJECTED.toException(t, message)
}

interface CommandHandlerSideEffectHandler {
  val notifications: List<suspend () -> Unit>
  val chainedCommands: List<CommandInput>

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
