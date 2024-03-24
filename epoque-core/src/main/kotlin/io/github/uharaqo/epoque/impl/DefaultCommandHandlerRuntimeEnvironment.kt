package io.github.uharaqo.epoque.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutputMetadata
import io.github.uharaqo.epoque.builder.CommandHandlerOutputCollector
import io.github.uharaqo.epoque.builder.CommandHandlerSideEffectHandler
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironment
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironmentFactory
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironmentFactoryFactory
import io.github.uharaqo.epoque.builder.WithPreparedParam
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

interface PreparedCommandHandler<C, S, E> : CommandHandler<C, S, E> {
  val prepare: suspend (c: C) -> Any?
}

object DeserializedCommand : EpoqueContextKey<Any>

class DefaultEpoqueRuntimeEnvironmentFactoryFactory : EpoqueRuntimeEnvironmentFactoryFactory {

  override fun create(
    commandRouter: CommandRouter,
    environment: EpoqueEnvironment,
  ): EpoqueRuntimeEnvironmentFactory<Any, Any?, Any> =
    DefaultEpoqueRuntimeEnvironmentFactory(commandRouter, environment.eventReader)
}

class DefaultEpoqueRuntimeEnvironmentFactory(
  private val commandRouter: CommandRouter,
  private val journalChecker: JournalChecker,
) : EpoqueRuntimeEnvironmentFactory<Any, Any?, Any>, CommandRouter by commandRouter {

  override suspend fun create(): EpoqueRuntimeEnvironment<Any, Any?, Any> {
    val outputCollector = DefaultCommandHandlerOutputCollector<Any>()
    val sideEffectHandler =
      DefaultCommandHandlerSideEffectHandler(journalChecker, commandRouter.commandCodecRegistry)
    return DefaultEpoqueRuntimeEnvironment(outputCollector, sideEffectHandler, this)
  }

  override suspend fun process(input: CommandInput): Failable<CommandOutput> {
    val workflow = create()
    return EpoqueContext.with({ put(EpoqueRuntimeEnvironment, workflow) }) {
      commandRouter.process(input)
    }
  }
}

private class DefaultEpoqueRuntimeEnvironment(
  private val outputCollector: CommandHandlerOutputCollector<Any>,
  private val sideEffectHandler: CommandHandlerSideEffectHandler,
  private val commandRouter: CommandRouter,
) :
  EpoqueRuntimeEnvironment<Any, Any?, Any>,
  CommandHandlerOutputCollector<Any> by outputCollector,
  CommandHandlerSideEffectHandler by sideEffectHandler,
  WithPreparedParam {

  private lateinit var x: Optional<Any>

  @Suppress("UNCHECKED_CAST")
  override fun <X> getPreparedParam(): X? = x.orElse(null) as X?

  override suspend fun beforeBegin(context: CommandContext) {
    @Suppress("UNCHECKED_CAST")
    val commandHandler = CommandHandler.get()?.let { it as CommandHandler<Any?, *, *> }
    x =
      if (commandHandler !is PreparedCommandHandler<Any?, *, *>) {
        Optional.empty()
      } else {
        try {
          Optional.ofNullable(commandHandler.prepare.invoke(DeserializedCommand.get()))
        } catch (e: Exception) {
          throw EpoqueException.Cause.COMMAND_PREPARATION_FAILURE.toException(e)
        }
      }
  }

  override suspend fun beforeCommit(output: CommandOutput) {
    sideEffectHandler.chainedCommands.forEach { command ->
      // TODO: result handling
      commandRouter.process(command).getOrElse { throw it }
    }
  }

  override suspend fun afterCommit(output: CommandOutput) {
    // TODO: exception should be caught?
    sideEffectHandler.notifications.forEach { it.invoke() }
  }
}

/** Created for each command request to collect outputs */
private class DefaultCommandHandlerOutputCollector<E> : CommandHandlerOutputCollector<E> {
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

private class DefaultCommandHandlerSideEffectHandler(
  private val journalChecker: JournalChecker,
  private val commandCodecRegistry: CommandCodecRegistry,
) : CommandHandlerSideEffectHandler {
  private val _chainedCommands = ConcurrentLinkedQueue<CommandInput>()
  private val _notifications = ConcurrentLinkedQueue<suspend () -> Unit>()

  override val chainedCommands: List<CommandInput>
    get() = _chainedCommands.toList()
  override val notifications: List<suspend () -> Unit>
    get() = _notifications.toList()

  override suspend fun exists(key: JournalKey): Boolean =
    journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }

  override fun notify(block: suspend () -> Unit) {
    _notifications += block
  }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      commandCodecRegistry.find<Any>(type).flatMap { it.encode(command) }
        .getOrElse { throw it }
    _chainedCommands += CommandInput(id, type, serialized, metadata, options)
  }
}
